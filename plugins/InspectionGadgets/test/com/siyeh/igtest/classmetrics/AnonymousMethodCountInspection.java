package com.siyeh.igtest.classmetrics;

public class AnonymousMethodCountInspection {
    public void foo()
    {
        Runnable runnable = new Runnable() {
            public void run() {
                foo();
            }
            public void run2() {
                foo();
            }
        };
    }
}
