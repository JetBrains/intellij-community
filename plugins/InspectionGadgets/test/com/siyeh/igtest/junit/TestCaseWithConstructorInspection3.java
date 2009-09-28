package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestCaseWithConstructorInspection3 extends TestCase
{
    public TestCaseWithConstructorInspection3()
    {
        super();
        System.out.println("TestCaseWithConstructorInspection3.TestCaseWithConstructorInspection3");
    }

}
