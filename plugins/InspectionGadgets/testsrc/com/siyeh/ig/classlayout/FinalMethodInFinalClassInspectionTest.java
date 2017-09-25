package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class FinalMethodInFinalClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/final_method_in_final_class",
           new FinalMethodInFinalClassInspection());
  }
}