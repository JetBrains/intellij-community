package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestMethodWithNoAssertionsInspection extends TestCase
{
    public TestMethodWithNoAssertionsInspection()
    {
    }

    public void test()
    {

    }

    public void test2()
    {
        assertTrue(true);
    }

    public void test3()
    {
        fail();
    }
}
