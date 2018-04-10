package com.siyeh.igtest.inheritance.refused_bequest;

import org.junit.Before;

import java.util.ArrayList;

public class RefusedBequest extends ArrayList {

  @Override
  public int <warning descr="Method 'size()' does not call 'super.size()'">size</warning>() {
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
  public String <warning descr="Method 'toString()' does not call 'super.toString()'">toString</warning>() {
    return "B.toString";
  }
}
class C {
  @Before
  public void setUp() {}
}
class D extends C {
  @Override
  public void <warning descr="Method 'setUp()' does not call 'super.setUp()'">setUp</warning>() {

  }
}
class E extends C {
  @Override
  public void setUp() {
    Runnable r = super::setUp;
    r.run();
  }
}
class Deny extends ArrayList {
  @Override
  public int size() {
    throw new UnsupportedOperationException();
  }
}
interface Black {
  void in();
}
class Back implements Black {
  @Override
  public void in() {
    System.out.println("tumulteous");
  }
}