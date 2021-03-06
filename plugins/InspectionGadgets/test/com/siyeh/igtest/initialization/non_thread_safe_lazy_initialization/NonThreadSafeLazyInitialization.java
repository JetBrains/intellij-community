package com.siyeh.igtest.threading;

public class NonThreadSafeLazyInitialization {
    private static Object foo1;
    private static Object foo2;
    private static Object foo3;
    private static Object foo4;
    private static Object foo5;
    private static Object foo6;
    private static Object foo7;
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

    {
        if (foo2 == null) {
            <warning descr="Lazy initialization of 'static' field 'foo2' is not thread-safe"><caret>foo2</warning> = new Object();
        }

    }

    public void instMethod() {
        if (foo3 == null) {
            (<warning descr="Lazy initialization of 'static' field 'foo3' is not thread-safe">foo3</warning>) = new Object();
        }
    }

    public static void staticMethod() {
        if (foo4 == null) {
            <warning descr="Lazy initialization of 'static' field 'foo4' is not thread-safe">foo4</warning> = new Object();
        }
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

    public Object getInstance3() {
        if (foo7 == null) <warning descr="Lazy initialization of 'static' field 'foo7' is not thread-safe">foo7</warning> = "";
        return foo7;
    }
}
