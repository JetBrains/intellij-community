package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryContinueInspectionTest extends IGInspectionTestCase {

  public void test() {
    final UnnecessaryContinueInspection inspection = new UnnecessaryContinueInspection();
    inspection.ignoreInThenBranch = true;
    doTest("com/siyeh/igtest/controlflow/unnecessary_continue", inspection);
  }
}
