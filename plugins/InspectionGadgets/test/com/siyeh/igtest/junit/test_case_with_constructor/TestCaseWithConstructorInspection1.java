package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestCaseWithConstructorInspection1 extends TestCase
{
    public <warning descr="Initialization logic in constructor 'TestCaseWithConstructorInspection1()' instead of 'setUp()'">TestCaseWithConstructorInspection1</warning>()
    {
        System.out.println("");
    }

}
