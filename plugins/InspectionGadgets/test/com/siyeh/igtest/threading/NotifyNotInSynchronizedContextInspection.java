package com.siyeh.igtest.threading;

public class NotifyNotInSynchronizedContextInspection
{
    private final Object lock = new Object();

    public  void foo()
    {
        lock.notify();
    }
    public  synchronized void bar()
    {
        lock.notify();
    }

    public  void barzoomb() {
        synchronized (lock) {
            lock.notify();
        }
    }
    
    public  void fooAll()
    {
        lock.notifyAll();
    }
    public  synchronized void barAll()
    {
        lock.notifyAll();
    }

    public  void barzoombAll() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
