package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class UtilityClassWithPublicConstructorInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/utility_class_with_public_constructor",
           new UtilityClassWithPublicConstructorInspection());
  }
}