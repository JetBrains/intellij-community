package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;

public class SignalWithoutAwaitnspection {
    private final Condition x = null;

    public void foo() throws InterruptedException {
        x.signal();
        x.signalAll();
    }
}
