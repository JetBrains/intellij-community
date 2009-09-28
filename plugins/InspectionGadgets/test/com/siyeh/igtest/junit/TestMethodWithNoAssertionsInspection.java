package com.siyeh.igtest.junit;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Assert;

public class TestMethodWithNoAssertionsInspection extends TestCase
{
    public TestMethodWithNoAssertionsInspection()
    {
    }

    public void test()
    {

    }

    @Test
    public void fourOhTest()
    {

    }

    @Test(expected = Exception.class)
    public void fourOhTestWithExpected()
    {

    }

    @Test
    public void fourOhTest2()
    {
        Assert.assertTrue(true);
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
