package com.siyeh.igtest.bugs.throwable_instance_never_thrown;

import java.io.IOException;
import java.util.*;

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

    void exceptionIsCollected() {
        List<IOException> exs = new ArrayList<IOException>();
        exs.add(new IOException());
        IOException io2 = new IOException();
        exs.add(io2);
        methodCall(io2);
    }

    void methodCall(IOException e){}
}
