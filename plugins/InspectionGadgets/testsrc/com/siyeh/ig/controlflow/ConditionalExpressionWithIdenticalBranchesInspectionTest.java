package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class ConditionalExpressionWithIdenticalBranchesInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/conditional_expression_with_identical_branches",
           new ConditionalExpressionWithIdenticalBranchesInspection());
  }
}
