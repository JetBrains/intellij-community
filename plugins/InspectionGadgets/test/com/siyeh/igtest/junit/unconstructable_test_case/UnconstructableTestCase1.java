package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class <warning descr="Test class 'UnconstructableTestCase1' is not constructable because it does not have a 'public' no-arg constructor">UnconstructableTestCase1</warning> extends TestCase
{
    private UnconstructableTestCase1()
    {
        System.out.println("");
    }

}
