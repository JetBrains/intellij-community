package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SynchronizationOnLockObjectInspection
{
    private final Lock lock = new ReentrantLock();



    public  void barzoomb() throws InterruptedException {
        synchronized (lock) {
        }
    }
}
