package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class UnconstructableTestCase2 extends TestCase
{
    public UnconstructableTestCase2(Object foo)
    {
        System.out.println("");
    }

}
