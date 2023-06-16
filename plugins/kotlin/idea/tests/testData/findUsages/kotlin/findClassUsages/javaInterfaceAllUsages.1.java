package client;

import server.Server;

class Client<T> {
    private Server myServer = new Server() {};

    public void setServer(Server server) {
        myServer = server;
    }

    public Server getMyServer() {
        return myServer;
    }

    void genericArg(Client<Server> client) {}

    void takeObject(Object o) {
        Server server = (Server) o;
    }

    boolean testInstanceOf(Object o) {
        return o instanceof Server;
    }

    public  void useJvmFieldFromKotlin() {
        String d =  Server.ID;
        Server.callStatic();
    }

}