package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryUnboxingInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/unnecessary_unboxing", new UnnecessaryUnboxingInspection());
  }

  public void testSuperfluous() throws Exception {
    final UnnecessaryUnboxingInspection tool = new UnnecessaryUnboxingInspection();
    tool.onlyReportSuperfluouslyUnboxed = true;
    doTest("com/siyeh/igtest/migration/unnecessary_superfluous_unboxing", tool);
  }
}