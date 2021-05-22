package com.siyeh.igtest.threading;

public class NonThreadSafeLazyInitialization {
    private static Object foo1;
    private static Object foo3;
    private static Object foo5;
    private static Object foo6;
    private Object instance;

    public Object getInstance() {
        if (instance == null) {
            instance = new Object();
        }
        return instance;
    }

    static
    {
        if (foo1 == null) {
            foo1 = new Object();
        }

    }

    private static final class Foo2Holder {
        static final Object foo2 = new Object();
    }

    {

    }

    public void instMethod() {
        if (foo3 == null) {
            (foo3) = new Object();
        }
    }

    private static final class Foo4Holder {
        static final Object foo4 = new Object();
    }

    public static void staticMethod() {
    }

    public void lockedInstMethod() {
        synchronized (NonThreadSafeLazyInitialization.class) {
            if (foo5 == null) {
                foo5 = new Object();
            }
        }
    }

    private static String example = null;

    public  Object getInstance2() {
        if (foo6 == null) {
            while (true) {
                foo6 = "";
            }
        }
        return foo6;
    }

    private static final class Foo7Holder {
        static final Object foo7 = "";
    }

    public Object getInstance3() {
        return Foo7Holder.foo7;
    }
}
