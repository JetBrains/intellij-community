package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class SwitchStatementWithoutDefaultInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/switch_statements_without_default",
           new SwitchStatementsWithoutDefaultInspection());
  }
}