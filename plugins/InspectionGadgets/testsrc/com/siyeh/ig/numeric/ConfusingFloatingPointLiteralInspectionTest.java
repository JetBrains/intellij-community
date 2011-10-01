package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class ConfusingFloatingPointLiteralInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/confusing_floating_point_literal",
           new ConfusingFloatingPointLiteralInspection());
  }
}