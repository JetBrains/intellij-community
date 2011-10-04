package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnclearBinaryExpressionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unclear_binary_expression",
           new UnclearBinaryExpressionInspection());
  }
}