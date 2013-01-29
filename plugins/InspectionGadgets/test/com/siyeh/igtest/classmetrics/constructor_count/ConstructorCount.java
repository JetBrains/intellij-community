package com.siyeh.igtest.classmetrics.constructor_count;

public class ConstructorCount {

  ConstructorCount() {}
  ConstructorCount(String s) {}

  @Deprecated
  ConstructorCount(int i) {}

  class A {
    A() {}
    A(int i) {}
    A(String s) {}
  }
}
