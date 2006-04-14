package com.siyeh.igtest.junit;

import static org.junit.Assert.*;
import org.junit.Test;

public class SimplifiableJUnit40AssertionInspection {
    @Test
    public void test()
    {
        assertTrue(3 == 4);
        assertEquals(false, new Object() != null);
        assertTrue(false);
        assertFalse("foo", true);
    }
}
