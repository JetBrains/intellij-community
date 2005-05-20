package com.siyeh.igtest.threading;

public class NonThreadSafeLazyInitializationInspection {
    private static Object foo;

    static
    {
        if (foo == null) {
            foo = new Object();
        }

    }

    {
        if (foo == null) {
            foo = new Object();
        }

    }

    public void instMethod() {
        if (foo == null) {
            foo = new Object();
        }
    }

    public void lockedInstMethod() {
        synchronized (NonThreadSafeLazyInitializationInspection.class) {
            if (foo == null) {
                foo = new Object();
            }
        }
    }
}
