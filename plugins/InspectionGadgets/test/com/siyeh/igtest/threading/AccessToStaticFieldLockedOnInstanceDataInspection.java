package com.siyeh.igtest.threading;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AccessToStaticFieldLockedOnInstanceDataInspection {
    private static int foo;

    public static synchronized void test1()
    {
        foo = 3;
        System.out.println(foo);
    }

    public synchronized void test2()
    {
        foo = 3;
        System.out.println(foo);
    }

    public void foo()
    {
        foo = 3;
        synchronized(this)
        {
            foo = 3;
            System.out.println(foo);
        }
    }
}
class StaticFieldNotLockedOnInstanceData
{
    private static final Object printer_ = new Object();
    ;

    private final Executor executor_ = Executors.newCachedThreadPool();
    private final Object lock_ = new Object();

    void print()
    {
        synchronized (lock_)
        {
            executor_.execute(new Runnable()
            {
                @Override public void run()
                {
                    printer_.hashCode();
                }
            });
        }
    }
}
