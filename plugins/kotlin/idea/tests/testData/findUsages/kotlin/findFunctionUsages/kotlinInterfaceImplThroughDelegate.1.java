package client;

import server.InterfaceWithDelegatedWithImpl;

public class Test {
    public static void bar(InterfaceWithDelegatedWithImpl some) {
        some.foo();
    }
}
