package com.siyeh.igtest.threading;

public class WaitWhileHoldingTwoLocks
{
    private final Object lock = new Object();
    private final Object lock2 = new Object();

    public  void foo() throws InterruptedException {
        synchronized (lock2) {
            synchronized (lock) {
                lock.<warning descr="Call to 'wait()' is made while holding two locks">wait</warning>();
            }
        }

    }
    public  synchronized void bar() throws InterruptedException {
        synchronized (lock) {
            lock.<warning descr="Call to 'wait()' is made while holding two locks">wait</warning>();
        }
    }

    public  void barzoomb() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }
}
