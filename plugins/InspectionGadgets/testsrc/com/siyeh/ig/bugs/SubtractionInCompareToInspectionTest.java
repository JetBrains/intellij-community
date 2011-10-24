package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class SubtractionInCompareToInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/subtraction_in_compare_to", new SubtractionInCompareToInspection());
  }
}