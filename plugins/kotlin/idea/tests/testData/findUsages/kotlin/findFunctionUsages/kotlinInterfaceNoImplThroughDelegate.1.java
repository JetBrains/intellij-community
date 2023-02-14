package client;

import server.InterfaceWithDelegatedNoImpl;

public class JClient {
    public static void bar(InterfaceWithDelegatedNoImpl some) {
        some.foo();
    }
}