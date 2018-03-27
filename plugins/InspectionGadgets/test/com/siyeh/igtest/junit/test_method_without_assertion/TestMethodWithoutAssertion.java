package com.siyeh.igtest.junit;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Assert;
import mockit.*;

public class TestMethodWithoutAssertion extends TestCase
{
    public TestMethodWithoutAssertion()
    {
    }

    public void <warning descr="JUnit test method 'test()' contains no assertions">test</warning>()
    {

    }

    @Test
    public void <warning descr="JUnit test method 'fourOhTest()' contains no assertions">fourOhTest</warning>()
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

    @Test
    public void delegateOnly() {
        check();
    }

    @Test
    public void delegateAdditionally() {
        final int i = 9;
        check();
    }

    private void check() {
        Assert.assertTrue(true);
    }

    @Test
    public void testExecuteReverseAcknowledgement(@Mocked final Object messageDAO)  {
        System.out.println(messageDAO);

        new Verifications() {{
            messageDAO.toString();
        }};
    }

    @Test
    public void testMethodWhichThrowsExceptionOnFailure() throws AssertionError {
        if (true) throw new AssertionError();
    }
}
