package usages;

import testing.Server;

class Client {
    public void foo() {
        new Server().processRequest();
        new ServerEx().processRequest();
    }
}
