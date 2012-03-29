package com.siyeh.ig.methodmetrics;

import com.siyeh.ig.IGInspectionTestCase;

public class CyclomaticComplexityInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final CyclomaticComplexityInspection tool = new CyclomaticComplexityInspection();
    tool.ignoreEqualsMethod = true;
    doTest("com/siyeh/igtest/methodmetrics/cyclomatic_complexity", tool);
  }
}