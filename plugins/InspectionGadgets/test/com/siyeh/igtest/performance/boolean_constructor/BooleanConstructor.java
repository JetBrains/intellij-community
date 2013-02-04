package com.siyeh.igtest.performance.boolean_constructor;

public class BooleanConstructor {

  void foo(boolean b) {
    Boolean b1 = new Boolean();
    Boolean b2 = new Boolean(b);
    Boolean b3 = new Boolean(true);
  }
}