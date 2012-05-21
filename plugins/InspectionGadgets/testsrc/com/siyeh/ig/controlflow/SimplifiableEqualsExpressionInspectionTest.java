package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class SimplifiableEqualsExpressionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/simplifiable_equals_expression",
           new SimplifiableEqualsExpressionInspection());
  }
}
