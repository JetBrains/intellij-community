package com.siyeh.igtest.inheritance.refused_bequest;

import java.util.ArrayList;

public class RefusedBequest extends ArrayList {

  @Override
  public int size() {
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