package com.siyeh.igtest.threading.access_to_static_field_locked_on_instance_data;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AccessToStaticFieldLockedOnInstanceData {
    private static int foo;
    private static final List LIST = new java.util.ArrayList(); // pretend this is immutable

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
            LIST.get(0);
        }
    }
}
class StaticFieldNotLockedOnInstanceData
{
    private static final Object printer_ = new Object();

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
