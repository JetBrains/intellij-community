package com.siyeh.igtest.exceptionHandling;

public class EmptyCatchBlockInspection
{
    public void foo()
    {
        try
        {
            throw new Exception();
        }
        catch(Exception e)
        {
        }
        try
        {
            throw new Exception();
        }
        catch(Exception e)
        {
            //catch comment
        }
    }
}
