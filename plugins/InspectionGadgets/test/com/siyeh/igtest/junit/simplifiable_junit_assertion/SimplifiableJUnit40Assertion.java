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
        Boolean b = true;
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assertTrue</warning>(b == true);
        int i1 = 0; int i2 = 0;
        <warning descr="'assertFalse()' can be simplified to 'assertNotEquals()'">assertFalse</warning>(i1 == i2);
        //difference with junit 5
        org.junit.jupiter.api.Assertions.assertFalse(i1 == i2);
        org.junit.jupiter.api.Assertions.<warning descr="'assertFalse()' can be simplified to 'assertNotEquals()'">assertFalse</warning>(new Object().equals(new Object()));
    }
}
