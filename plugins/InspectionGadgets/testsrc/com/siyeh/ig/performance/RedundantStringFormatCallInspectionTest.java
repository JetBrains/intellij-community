package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class RedundantStringFormatCallInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/redundant_string_format_call",
           new RedundantStringFormatCallInspection());
  }
}