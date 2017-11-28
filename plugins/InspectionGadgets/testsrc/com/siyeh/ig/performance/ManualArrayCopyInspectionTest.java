package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class ManualArrayCopyInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/performance/manual_array_copy",
           new ManualArrayCopyInspection());
  }
}