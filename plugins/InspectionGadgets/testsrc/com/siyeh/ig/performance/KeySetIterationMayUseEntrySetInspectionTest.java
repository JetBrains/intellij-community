package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class KeySetIterationMayUseEntrySetInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/key_set_iteration_may_use_entry_set",
           new KeySetIterationMayUseEntrySetInspection());
  }
}