package com.siyeh.ig.portability;

import com.siyeh.ig.IGInspectionTestCase;

public class SystemExitInspectionTest extends IGInspectionTestCase {

  public void test() {
    final SystemExitInspection tool = new SystemExitInspection();
    tool.ignoreInMainMethod = true;
    doTest("com/siyeh/igtest/portability/system_exit", tool);
  }
}
