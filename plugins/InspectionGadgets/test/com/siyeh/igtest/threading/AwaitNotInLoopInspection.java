package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.sql.Date;

public class AwaitNotInLoopInspection
{
    private Object lock;

    public  void foo()
    {
        Condition condition;
        try
        {
            lock.wait();
            condition.await();
            condition.awaitUninterruptibly();
            condition.awaitNanos(300);
            condition.awaitUntil(new Date());
            condition.await(300, TimeUnit.MICROSECONDS);
        }
        catch(InterruptedException e)
        {
        }
    }
}
