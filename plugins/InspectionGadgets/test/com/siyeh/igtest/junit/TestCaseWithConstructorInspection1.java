package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestCaseWithConstructorInspection1 extends TestCase
{
    public TestCaseWithConstructorInspection1()
    {
        System.out.println("");
    }

}
