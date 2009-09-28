package com.siyeh.igtest.junit;

import junit.framework.TestCase;

public class SetupCallsSuperSetupInspection extends TestCase{
    protected void setUp() throws Exception {
        System.out.println("foo");
    }

    protected void tearDown() throws Exception {
        System.out.println("bar");
    }
}
