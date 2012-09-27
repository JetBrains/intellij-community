package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationInLoopsInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final StringConcatenationInLoopsInspection tool = new StringConcatenationInLoopsInspection();
    tool.m_ignoreUnlessAssigned = false;
    doTest("com/siyeh/igtest/performance/string_concatenation_in_loops", tool);
  }
}