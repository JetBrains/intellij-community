package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class <warning descr="Test case 'UnconstructableTestCase2' is not constructable by most test runners">UnconstructableTestCase2</warning> extends TestCase
{
    public UnconstructableTestCase2(Object foo)
    {
        System.out.println("");
    }

}
