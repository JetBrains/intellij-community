package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class SwitchStatementWithTooFewBranchesInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/controlflow/switch_statement_with_too_few_branches", new SwitchStatementWithTooFewBranchesInspection());
  }
}
