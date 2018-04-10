package com.siyeh.igtest.style.unqualified_method_access;

import javax.swing.*;

public class UnqualifiedMethodAccess extends JPanel {
  public UnqualifiedMethodAccess(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  void foo() {}
  void bar() {
    <warning descr="Instance method call 'foo' is not qualified with 'this'">foo</warning>();
  }

  void foo(String s) {
    this.foo();
    class A {
      void a() {
        <warning descr="Instance method call 'a' is not qualified with 'this'">a</warning>();
        new Object() {
          void b() {
            <warning descr="Instance method call 'a' is not qualified with 'this'">a</warning>();
          }
        };
      }
    }
  }

  void anonymous() {
    new Object() {
      void bar() {
        new Object() {
          void foo() {
            <warning descr="Instance method call 'bar' is not qualified with 'this'">bar</warning>();
            <warning descr="Instance method call 'foo' is not qualified with 'this'">foo</warning>();
          }
        };
      }
    };
  }
}
