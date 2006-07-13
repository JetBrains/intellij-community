package com.siyeh.igtest.threading;

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
