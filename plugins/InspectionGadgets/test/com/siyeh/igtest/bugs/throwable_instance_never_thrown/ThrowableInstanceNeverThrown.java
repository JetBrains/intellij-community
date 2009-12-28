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

    void throwing() throws Throwable {
        throw new RuntimeException("asdf").initCause(null);
    }

    void alsoThrowing() throws IllegalArgumentException {
        throw (IllegalArgumentException) new IllegalArgumentException("asdf").initCause(null);
    }

    void throwingTheThird() throws Throwable {
        final RuntimeException e = new RuntimeException("throw me");
        throw e;
    }

    void leftBehind() throws Throwable {
        final RuntimeException e = new RuntimeException("throw me");
    }
}
