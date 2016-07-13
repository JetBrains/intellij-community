package com.siyeh.igtest.bugs.throwable_instance_never_thrown;

import java.io.IOException;
import java.util.*;

public class ThrowableInstanceNeverThrown {

    private Throwable stop = new RuntimeException();

    void foo() throws Exception {
        try {
            System.out.println("");
        } catch (Throwable throwable) {
            throw (throwable instanceof Exception) ? (Exception)throwable : new Exception(throwable);
        }
    }

    void bar() {
        <warning descr="Runtime exception instance 'new RuntimeException()' is not thrown">new RuntimeException()</warning>;
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
        final RuntimeException e = <warning descr="Runtime exception instance 'new RuntimeException(\"throw me\")' is not thrown">new RuntimeException("throw me")</warning>;
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

interface I {
    Exception get();
}

class L {
    {
        I i = () -> new RuntimeException();

        final RuntimeException exception = new RuntimeException();
        I i2 = () -> exception;
    }
}