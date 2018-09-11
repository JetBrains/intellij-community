package com.siyeh.igtest.classmetrics.constructor_count;

public class ConstructorCount {

  ConstructorCount() {}
  ConstructorCount(String s) {}

  @Deprecated
  ConstructorCount(int i) {}

  class <warning descr="'A' has too many constructors (constructor count = 3)">A</warning> {
    A() {}
    A(int i) {}
    A(String s) {}
  }
}
