package client;

import client.InterfaceWithDelegatedWithImpl;

public class Test {
    public static void bar(InterfaceWithDelegatedWithImpl some) {
        some.foo();
    }
}
