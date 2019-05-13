package com.siyeh.igtest.threading.synchronized_method;

public class SynchronizedMethod {
    public <warning descr="Method 'fooBar()' declared 'synchronized'">synchronized</warning> void fooBar() {
        System.out.println("foo");
    }

    public static <warning descr="Method 'bar()' declared 'synchronized'">synchronized</warning> void bar() {
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
