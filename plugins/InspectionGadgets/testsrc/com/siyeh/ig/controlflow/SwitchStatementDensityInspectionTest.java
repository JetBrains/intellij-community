package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class SwitchStatementDensityInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/controlflow/switch_statement_density", new SwitchStatementDensityInspection());
  }
}