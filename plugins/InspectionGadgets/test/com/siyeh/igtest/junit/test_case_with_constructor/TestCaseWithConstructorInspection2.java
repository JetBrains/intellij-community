package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class TestCaseWithConstructorInspection2 extends TestCase
{
    public TestCaseWithConstructorInspection2()
    {
        super();
        ;
        if (false) {
            System.out.println();
        }
    }

}
