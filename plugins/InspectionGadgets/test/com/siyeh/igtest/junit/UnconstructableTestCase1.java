package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class UnconstructableTestCase1 extends TestCase
{
    private UnconstructableTestCase1()
    {
        System.out.println("");
    }

}
