package com.siyeh.igtest.bugs.throwable_instance_never_thrown;

public class ThrowableInstanceNeverThrown {

    void foo() throws Exception {
        try {
            System.out.println("");
        } catch (Throwable throwable) {
            throw (throwable instanceof Exception) ? (Exception)throwable : new Exception(throwable);
        }
    }

    void bar() {
        new RuntimeException();
    }
}
