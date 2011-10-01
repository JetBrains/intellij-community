package com.siyeh.ig.bitwise;

import com.siyeh.ig.IGInspectionTestCase;

public class PointlessBitwiseExpressionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bitwise/pointless_bitwise_expression",
           new PointlessBitwiseExpressionInspection());
  }
}