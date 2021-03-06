package chat.server.app;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ServerWorker extends Thread {

    // client
    private String id;
    private String name;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ServerMain server;

    ServerWorker(ServerMain server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void run() {
        System.out.println("Connected to " + id + ".");

        try {
            String line;

            while ((line = in.readLine()) != null && !line.equals("QUIT")) {
                String tokens[] = line.split("\\s+");
                System.out.println("Received from " + (name == null ? id : name) + ":\n  " + line);

                if (name == null) {
                    if (tokens[0].equals("NAME")) {
                        if (server.workers.containsKey(tokens[1])) {
                            sendCommand("NAME taken");
                        } else {
                            name = tokens[1];
                            broadcastCommand("ONLINE new " + name);
                            server.workers.put(name, this);
                            sendCommand("NAME ok");
                        }
                    }
                } else {
                    switch (tokens[0]) {
                        case "ONLINE":
                            if (tokens[1].equals("all")) {
                                sendCommand("ONLINE all " + String.join(" ", new ArrayList<>(server.workers.keySet())));
                            }
                            break;
                        case "SEND":
                            String message = Utils.extractMessage(line);
                            if (tokens[1].equals("all")) {
                                for (ServerWorker worker : server.workers.values()) {
                                    if (!worker.name.equals(name)) {
                                        worker.sendCommand("RECEIVE " + name + " " + message);
                                    }
                                }
                            } else {
                                ServerWorker recipient = server.workers.get(tokens[1]);
                                if (recipient != null) {
                                    recipient.sendCommand("RECEIVE " + name + " " + message);
                                } else {
                                    sendCommand("ERROR recipient");
                                }
                            }
                            break;
                        default:
                            System.err.println("Unknown command " + tokens[0] + ".");
                            break;
                    }
                }
            }

            logout();
        } catch (IOException e) {
            logout();
            System.out.println("Connection to " + (name != null ? name : id) + " was abruptly closed.");
        }
    }

    private void logout() {
        if (name != null) {
            server.workers.remove(name);
            broadcastCommand("OFFLINE " + name);
        }
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastCommand(String message) {
        for (ServerWorker worker : server.workers.values()) {
            worker.sendCommand(message);
        }
        System.out.println("Sent to all:\n  " + message);
    }

    private void sendCommand(String message) {
        out.println(message);
        System.out.println("Sent to " + (name != null ? name : id) + ":\n  " + message);
    }

}
