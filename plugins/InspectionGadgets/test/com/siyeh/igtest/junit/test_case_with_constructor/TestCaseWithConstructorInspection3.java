package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestCaseWithConstructorInspection3 extends TestCase
{
    public <warning descr="Initialization logic in constructor 'TestCaseWithConstructorInspection3()' instead of 'setUp()'">TestCaseWithConstructorInspection3</warning>()
    {
        super();
        System.out.println("TestCaseWithConstructorInspection3.TestCaseWithConstructorInspection3");
    }

}
