package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class <warning descr="Test class 'UnconstructableTestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableTestCase2</warning> extends TestCase
{
    public UnconstructableTestCase2(Object foo)
    {
        System.out.println("");
    }

}
