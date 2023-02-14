package client;

import server.*;

class Client {
    void fooBar() {
        A.Companion.setFoo("a");
        System.out.println("a.foo = " + A.Companion.getFoo());
    }
}