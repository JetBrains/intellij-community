package com.siyeh.igtest.inheritance.refused_bequest;

import org.junit.Before;

import java.util.ArrayList;

public class RefusedBequest extends ArrayList {

  @Override
  public int <warning descr="Method 'size()' ignores defined method in superclass">size</warning>() {
    return 0;
  }
}
class A {
  @Override
  public String toString() {
    return "A.toString";
  }
}

class B extends A {
  @Override
  public String toString() {
    return "B.toString";
  }
}
class C {
  @Before
  public void setUp() {}
}
class D extends C {
  @Override
  public void <warning descr="Method 'setUp()' ignores defined method in superclass">setUp</warning>() {

  }
}