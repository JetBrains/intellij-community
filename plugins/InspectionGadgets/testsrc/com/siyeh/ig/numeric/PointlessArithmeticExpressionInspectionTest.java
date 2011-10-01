package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class PointlessArithmeticExpressionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/pointless_arithmetic_expression",
           new PointlessArithmeticExpressionInspection());
  }
}