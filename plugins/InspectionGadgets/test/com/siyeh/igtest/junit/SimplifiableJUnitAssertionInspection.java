package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class SimplifiableJUnitAssertionInspection extends TestCase{
    public void test()
    {
        assertTrue(3 == 4);
        assertEquals(false, new Object() != null);
    }
}
