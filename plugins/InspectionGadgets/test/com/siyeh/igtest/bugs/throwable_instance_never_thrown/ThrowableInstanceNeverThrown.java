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
        new <warning descr="Runtime exception instance 'new RuntimeException()' is not thrown">RuntimeException</warning>();
    }

    void suppressed() {
      //noinspection ThrowableInstanceNeverThrown
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
        final RuntimeException e = new <warning descr="Runtime exception instance 'new RuntimeException()' is not thrown">RuntimeException</warning>("throw me");
    }

    void saving() {
      try {

      } catch (Exception e) {
        String message = e.getMessage();
        if (message != null && message.length() > 1024) {
          Exception truncated = new RuntimeException(message.substring(0, 1024) + "...");
          truncated.setStackTrace(e.getStackTrace());
          e = truncated;
        }
        System.out.println(e);
      }
    }

    Throwable[] array() {
      return new Throwable[] { new RuntimeException() };
    }

    void poorMansDebug() {
      new Throwable().printStackTrace(System.out);
    }

    void exceptionIsCollected() {
        List<IOException> exs = new ArrayList<IOException>();
        exs.add(new IOException());
        IOException io2 = new IOException();
        exs.add(io2);
        methodCall(io2);
    }

    void methodCall(IOException e){}

    void iterating() {
        StackTraceElement[] stackTrace = new X().getStackTrace2();
        for (StackTraceElement stackTraceElement : stackTrace) {
            System.out.println(stackTraceElement);
        }
    }

    private void print(String text, java.io.PrintStream ps) {
        X asdf = new X();
        StackTraceElement[] element = asdf.getStackTrace2();
        StackTraceElement dumper = element[2];
        ps.println(text + " at " + dumper.toString());
    }

    class X extends Throwable {
      public StackTraceElement[] getStackTrace2() {
        return null;
      }
    }
    class StackTraceElement {}
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
class Main {
  Runnable r = () -> new <warning descr="Runtime exception instance 'new RuntimeException()' is not thrown">RuntimeException</warning>();

  Throwable switchExpression1(int i) {
    return switch(i) {
      default -> new Throwable();
    };
  }

  Throwable switchExpression2(int i) {
    for (int j = 0; j < 10; j++, new <warning descr="Throwable instance 'Throwable' is not thrown">Throwable</warning>()) {}

    return switch(i) {
      default -> {
        yield new Throwable();
      }
    };
  }

  void x(I i) {
    assert i.get() != null;
  }
}