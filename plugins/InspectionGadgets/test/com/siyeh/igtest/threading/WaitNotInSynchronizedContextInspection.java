package com.siyeh.igtest.threading;

public class WaitNotInSynchronizedContextInspection
{
    private final Object lock = new Object();

    public  void foo()
    {
        try
        {
            lock.wait();
        }
        catch(InterruptedException e)
        {
        }
    }
    public  synchronized void bar()
    {
        try
        {
            lock.wait();
        }
        catch(InterruptedException e)
        {
        }
    }

    public  void barzoomb() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }
}
