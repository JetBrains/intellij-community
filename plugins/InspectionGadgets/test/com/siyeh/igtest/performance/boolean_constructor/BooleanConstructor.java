package com.siyeh.igtest.performance.boolean_constructor;

public class BooleanConstructor {

  void foo(boolean b) {
    Boolean b1 = new Boolean<error descr="Cannot resolve constructor 'Boolean()'">()</error>;
    Boolean b2 = new <warning descr="Boolean constructor call">Boolean</warning>(b);
    Boolean b3 = new <warning descr="Boolean constructor call">Boolean</warning>(true);
  }
}