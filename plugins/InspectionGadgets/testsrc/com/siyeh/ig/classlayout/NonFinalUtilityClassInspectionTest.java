package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class NonFinalUtilityClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/non_final_utility_class", new NonFinalUtilityClassInspection());
  }
}