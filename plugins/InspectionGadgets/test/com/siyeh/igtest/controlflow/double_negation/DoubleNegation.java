package com.siyeh.igtest.controlflow.double_negation;

public class DoubleNegation {

  void negative(boolean b1, boolean b2, boolean b3) {
    boolean r1 = !(b1 != b2);
    boolean r2 = !!b1;
    boolean r3 = !b1 != b2;
    boolean r4 = (b1 != (b2 != b3));
    boolean r5 = (b1 != b2 != b3);
  }
}
