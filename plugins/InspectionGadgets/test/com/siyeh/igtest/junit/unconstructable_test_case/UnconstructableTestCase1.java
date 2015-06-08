package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class <warning descr="Test case 'UnconstructableTestCase1' is not constructable by most test runners">UnconstructableTestCase1</warning> extends TestCase
{
    private UnconstructableTestCase1()
    {
        System.out.println("");
    }

}
