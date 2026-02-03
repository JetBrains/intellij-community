package server;

public class JClient {
    public static void bar(InterfaceWithDelegatedNoImpl some) {
        some.foo();
    }
}