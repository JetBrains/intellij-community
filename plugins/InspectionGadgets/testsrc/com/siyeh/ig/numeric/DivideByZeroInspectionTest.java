package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class DivideByZeroInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/divide_by_zero", new DivideByZeroInspection());
  }
}