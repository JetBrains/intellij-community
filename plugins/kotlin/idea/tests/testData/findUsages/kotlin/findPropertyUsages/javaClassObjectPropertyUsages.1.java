package client;

import server.A;

class Client {
    void fooBar() {
        A.Companion.setFoo("a");
        A.foo = "a";
        System.out.println("a.foo = " + A.Companion.getFoo());
        System.out.println("a.foo = " + A.foo);
    }
}