package com.siyeh.igtest.threading;

public class WaitWhileHoldingTwoLocksInspection
{
    private final Object lock = new Object();
    private final Object lock2 = new Object();

    public  void foo() throws InterruptedException {
        synchronized (lock2) {
            synchronized (lock) {
                lock.wait();
            }
        }

    }
    public  synchronized void bar() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }

    public  void barzoomb() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }
}
