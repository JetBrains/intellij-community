package com.siyeh.igtest.threading;

public class WaitNotInLoopInspection
{
    private Object lock;

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
}
