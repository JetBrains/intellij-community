package com.siyeh.igtest.exceptionHandling;

public class ThreadDeathRethrownInspection
{
    public void foo()
    {
        try
        {
            System.out.println("foo");
        }
        catch(ThreadDeath e)
        {
            e.printStackTrace();
        }

        try
        {
            System.out.println("foo");
        }
        catch(ThreadDeath e)
        {
            e.printStackTrace();
            throw e;
        }
    }
}
