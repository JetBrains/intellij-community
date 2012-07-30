package com.siyeh.igtest.threading.synchronized_method;

public class SynchronizedMethod {
    public synchronized void fooBar() {
        System.out.println("foo");
    }

    public static synchronized void bar() {
        System.out.println("foo");
    }

    public synchronized native void fooBaz();

    static class X extends SynchronizedMethod {

        @Override
        public synchronized void fooBar() {
            super.fooBar();
        }
    }
}
