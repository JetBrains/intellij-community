package com.siyeh.igtest.style.unqualified_inner_class_access;

import java.util.Map.Entry;
import com.siyeh.igtest.style.unqualified_inner_class_access.A.X;
public class Test<T> {
    private Class<<warning descr="'Entry' is not qualified with outer class">Entry</warning>> entryClass;

    public Test() {
        <warning descr="'Entry' is not qualified with outer class">Entry</warning> entry;
        entryClass = <warning descr="'Entry' is not qualified with outer class">Entry</warning>.class;
    }

    public Test(int i) {
        final String test = Inner.TEST;
    }
    static class Inner {
        public static final String TEST = "test";
    }
}
class A {
  class X {}
}
class B extends A {
  void foo(X x) {}
}
class C {
  void m(<warning descr="'X' is not qualified with outer class">X</warning> x) {
    new A().new X();
  }
}
class Outer {
  Outer getAnonymousClass() {
    return new Outer() {
      {
        Inner inner = new Inner();
      }

      class Inner { }
    };
  }
}