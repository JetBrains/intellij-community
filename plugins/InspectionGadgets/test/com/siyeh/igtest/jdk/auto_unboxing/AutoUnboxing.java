package com.siyeh.igtest.jdk.auto_unboxing;



public class AutoUnboxing {
    {
        Long someNumber = Long.valueOf(0);
        <warning descr="Auto-unboxing 'someNumber'">someNumber</warning>++;
        long l = <warning descr="Auto-unboxing 'someNumber'">someNumber</warning> + 0;
        Long aLong = Long.valueOf(<warning descr="Auto-unboxing 'someNumber'">someNumber</warning> << 2);
    }

    public void foo() {
        Integer bar = 3;
        int baz = <warning descr="Auto-unboxing 'new Integer(3)'">new Integer(3)</warning>;
        if (<warning descr="Auto-unboxing 'new Integer(3)'">new Integer(3)</warning> == 3) {
            return;
        }
        if (<warning descr="Auto-unboxing 'new Integer(3)'">new Integer(3)</warning> + 3 == 3) {
            return;
        }
        Integer x = 3;
    }


    public void bar(Double value) {
        if (<warning descr="Auto-unboxing 'value'">value</warning> > 0.0) {
            return;
        }

        bazz(<warning descr="Auto-unboxing 'value'">value</warning>);
    }

    private void bazz(double value) {
        System.out.println("value = " + value);
        Boolean c = <warning descr="Auto-unboxing 'Boolean.TRUE'">Boolean.TRUE</warning> & false;
        Long d = <warning descr="Auto-unboxing 'Integer.valueOf(2)'">Integer.valueOf(2)</warning> & 1L;
    }

    private boolean noWarn(Integer i1, Integer i2) {
      return i1 == i2;
    }

    void m(Boolean b) {
        assert <warning descr="Auto-unboxing 'b'">b</warning>;
    }

    void n(Integer i) {
      switch(<warning descr="Auto-unboxing 'i'">i</warning>) {
        case 1: break;
        case 2: break;
        case 3: break;
        default:
      }
    }

    void m(Object o, Boolean b, Number n) {
        if ((boolean) <warning descr="Auto-unboxing 'o'">o</warning>) {}
        if ((boolean) <warning descr="Auto-unboxing 'b'">b</warning>) {}
        if (<error descr="Incompatible types. Found: 'int', required: 'boolean'">(int)<warning descr="Auto-unboxing 'n'">n</warning></error>) {}
    }

    boolean polyadic() {
        return true && <warning descr="Auto-unboxing 'Boolean.TRUE'">Boolean.TRUE</warning> && true;
    }

  void n() {
    boolean b = Boolean.valueOf(true) ==<error descr="Expression expected"> </error>;
    boolean c = Boolean.valueOf(true) ==
                Boolean.valueOf(false) ==
                <warning descr="Auto-unboxing 'Boolean.valueOf(true)'">Boolean.valueOf(true)</warning>;
    boolean d = Boolean.valueOf(true) ==
                Boolean.valueOf(false);
    boolean e = <warning descr="Auto-unboxing 'Boolean.valueOf(true)'">Boolean.valueOf(true)</warning> ==
                false;
    boolean f = true ==
                <warning descr="Auto-unboxing 'Boolean.valueOf(false)'">Boolean.valueOf(false)</warning>;
    boolean g = Boolean.valueOf(true) == Boolean.valueOf(false) == true;
  }

  int polymorphicSignature(java.lang.invoke.MethodHandle mh) throws Throwable {
    return (int)mh.invokeExact();
  }

  void lambdas() {
    R r = () -> <warning descr="Auto-unboxing 'Integer.valueOf(1)'">Integer.valueOf(1)</warning>;
    R s = () -> {
      return <warning descr="Auto-unboxing 'Integer.valueOf(2)'">Integer.valueOf(2)</warning>;
    };
    R t = AutoUnboxing::<warning descr="Auto-unboxing 'bla'">bla</warning>;
    Runnable z = () -> {
      System.out.println();
    };
  }

  static Integer bla() {
    return Integer.valueOf(1);
  }

  interface R {
    int unbox();
  }
}
