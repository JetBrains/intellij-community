package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class FallthruInSwitchStatementInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/fallthru_in_switch_statement", new FallthruInSwitchStatementInspection());
  }
}