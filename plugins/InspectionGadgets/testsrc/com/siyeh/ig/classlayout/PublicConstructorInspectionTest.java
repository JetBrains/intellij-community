package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class PublicConstructorInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/public_constructor", new PublicConstructorInspection());
  }
}