package com.siyeh.igtest.classlayout;

public class LimitedScopeInnerClassInspection {
    public void foo() {
        class MyRunnable implements Runnable {
            public void run() {
            }
        }
        final Runnable runnable = new MyRunnable();
        runnable.run();
    }

}
