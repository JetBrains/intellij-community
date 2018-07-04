package com.siyeh.igtest.jdk.auto_boxing;

import java.util.Arrays;


public class AutoBoxing {

    static {
        Long someNumber = <warning descr="Auto-boxing '0L'">0L</warning>;
        Long aLong = <warning descr="Auto-boxing 'someNumber << 2'">someNumber << 2</warning>;
        Long other = <warning descr="Auto-boxing 'someNumber'">someNumber</warning>++;
        someNumber = <warning descr="Auto-boxing '~someNumber'">~someNumber</warning>;
        someNumber = <warning descr="Auto-boxing '-someNumber'">-someNumber</warning>;
        someNumber = <warning descr="Auto-boxing '+someNumber'">+someNumber</warning>;
    }

    public void foo() {
        Integer bar = <warning descr="Auto-boxing '3'">3</warning>;
        int baz = new Integer(3);
        if (new Integer(3) == 3) {
            return;
        }
        if (new Integer(3) + 3 == 3) {
            return;
        }
        Integer x = <warning descr="Auto-boxing '3'">3</warning>;
    }


    public void bar(Double value) {
        if (value > 0.0) { // this is not found!
            return;
        }

        bazz(value);
    }

    private void bazz(double value) {
        System.out.println("value = " + value);
        Boolean c = <warning descr="Auto-boxing 'Boolean.TRUE & false'">Boolean.TRUE & false</warning>;
        Long d = <warning descr="Auto-boxing 'Integer.valueOf(2) & 1L'">Integer.valueOf(2) & 1L</warning>;
    }

    void constantBoxing() {
        Byte s = <warning descr="Auto-boxing '8'">8</warning>;
        Short j = <warning descr="Auto-boxing '(byte)7'">(byte)7</warning>;
    }

    void polyadic() {
        Integer i = <warning descr="Auto-boxing '1 + 2 + 3'">1 + 2 + 3</warning>;
    }

    void doInstanceof(Object o) {
        Boolean b = <warning descr="Auto-boxing 'o instanceof String'">o instanceof String</warning>;
    }

    void m(boolean b) {
      System.out.println((Boolean)<warning descr="Auto-boxing 'b'">b</warning>);
      final Object o1 = (Object) <warning descr="Auto-boxing 'b'">b</warning>;
    }

    void polymorphicSignature(java.lang.invoke.MethodHandle meh) throws Throwable {
        meh.invokeExact(1);
    }

  void lambdas() {
    R r = () -> <warning descr="Auto-boxing '1'">1</warning>;
    R s = () -> {
      return <warning descr="Auto-boxing '2'">2</warning>;
    };
    R t = AutoBoxing::<warning descr="Auto-boxing 'bla'">bla</warning>;
    Runnable z = () -> {
      System.out.println();
    };
  }

  static int bla() {
    return 1;
  }

  interface R {
    Integer box();
  }

  enum NumberedLetter {
    A(<warning descr="Auto-boxing '3'">3</warning>);
    NumberedLetter(Integer i) {
    }
  }

  void varargs() {
      Arrays.asList(<warning descr="Auto-boxing ''a''">'a'</warning>, <warning descr="Auto-boxing ''b''">'b'</warning>, <warning descr="Auto-boxing ''c''">'c'</warning>);
  }
}
