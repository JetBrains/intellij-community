package com.siyeh.igtest.threading;

public class SynchronizedMethodInspection {
    public synchronized void fooBar() {
        System.out.println("foo");
    }

    public static synchronized void bar() {
        System.out.println("foo");
    }

    public synchronized native void fooBaz();
}
