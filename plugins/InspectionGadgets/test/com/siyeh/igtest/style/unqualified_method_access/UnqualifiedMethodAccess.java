package com.siyeh.igtest.style.unqualified_method_access;

import javax.swing.*;

public class UnqualifiedMethodAccess extends JPanel {
  public UnqualifiedMethodAccess(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  void foo() {}
  void bar() {
    foo();
  }

  void foo(String s) {
    this.foo();
  }
}
