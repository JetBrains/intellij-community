package com.siyeh.igtest.threading.access_to_static_field_locked_on_instance_data;

import java.util.List;



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
        <warning descr="Access to static field 'foo' locked on instance data">foo</warning> = 3;
        System.out.println(<warning descr="Access to static field 'foo' locked on instance data">foo</warning>);
    }

    public void foo()
    {
        foo = 3;
        synchronized(this)
        {
            <warning descr="Access to static field 'foo' locked on instance data">foo</warning> = 3;
            System.out.println(<warning descr="Access to static field 'foo' locked on instance data">foo</warning>);
            LIST.get(0);
        }
    }
}
class StaticFieldNotLockedOnInstanceData
{
    private static final Object printer_ = new Object();

    private final Object lock_ = new Object();

    void print()
    {
        synchronized (lock_)
        {
            new Thread(new Runnable()
            {
                @Override public void run()
                {
                    printer_.hashCode();
                }
            }).start();
        }
    }
}
