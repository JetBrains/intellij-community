package com.siyeh.igtest.exceptionHandling;

public class ErrorRethrownInspection
{
    public void foo()
    {
        try
        {
            System.out.println("foo");
        }
        catch(Error e)
        {
            e.printStackTrace();
        }

        try
        {
            System.out.println("foo");
        }
        catch(Error e)
        {
            e.printStackTrace();
            throw e;
        }

        try
        {
            System.out.println("foo");
        }
        catch(OutOfMemoryError e)
        {
            e.printStackTrace();
            throw e;
        }
        try
        {
            System.out.println("foo");
        }
        catch(OutOfMemoryError e)
        {
            e.printStackTrace();
        }
    }
}
