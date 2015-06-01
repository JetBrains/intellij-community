package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class UnconstructableTestCase4 extends TestCase
{
    public UnconstructableTestCase4(String foo)
    {
        System.out.println("");
    }

}
