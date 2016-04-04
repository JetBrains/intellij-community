package com.siyeh.igtest.junit;

import static org.junit.Assert.*;
import org.junit.Test;

public class SimplifiableJUnit40Assertion {
    @Test
    public void test()
    {
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assertTrue</warning>(3 == 4);
        <warning descr="'assertEquals()' can be simplified to 'assertFalse()'">assertEquals</warning>(false, new Object() != null);
        <warning descr="'assertTrue()' can be simplified to 'fail()'">assertTrue</warning>(false);
        <warning descr="'assertFalse()' can be simplified to 'fail()'">assertFalse</warning>("foo", true);
    }
}
