package com.siyeh.igtest.threading;

public class NonThreadSafeLazyInitialization {
    private static Object foo;
    private Object instance;

    public Object getInstance() {
        if (instance == null) {
            instance = new Object();
        }
        return instance;
    }

    static
    {
        if (foo == null) {
            foo = new Object();
        }

    }

    {
        if (foo == null) {
            <warning descr="Lazy initialization of 'static' field 'foo' is not thread-safe">foo</warning> = new Object();
        }

    }

    public void instMethod() {
        if (foo == null) {
            <warning descr="Lazy initialization of 'static' field 'foo' is not thread-safe">foo</warning> = new Object();
        }
    }

    public static void staticMethod() {
        if (foo == null) {
            <warning descr="Lazy initialization of 'static' field 'foo' is not thread-safe">foo</warning> = new Object();
        }
    }

    public void lockedInstMethod() {
        synchronized (NonThreadSafeLazyInitialization.class) {
            if (foo == null) {
                foo = new Object();
            }
        }
    }

    private static String example = null;

    public  Object getInstance2() {
        if (foo == null) {
            while (true) {
                foo = "";
            }
        }
        return foo;
    }

    public Object getInstance3() {
        if (foo == null) <warning descr="Lazy initialization of 'static' field 'foo' is not thread-safe">foo</warning> = "";
        return foo;
    }
}
