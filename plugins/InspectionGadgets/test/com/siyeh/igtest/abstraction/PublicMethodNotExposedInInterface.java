package com.siyeh.igtest.abstraction;

import junit.framework.TestCase;

public class PublicMethodNotExposedInInterface extends TestCase implements Interface {
    public void foo() {

    }

    public void baz() {
        bar2();
    }

    public static void bar() {

    }

    public void test() {
         fail();    
    }

    private void bar2() {

    }

}
