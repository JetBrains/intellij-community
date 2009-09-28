package com.siyeh.igtest.exceptionHandling;

import junit.framework.TestCase;

public class EmptyCatchBlockInspectionInTestCase extends TestCase
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
