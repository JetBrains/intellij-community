package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class ReturnFromFinallyBlockInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/errorhandling/return_from_finally_block", new ReturnFromFinallyBlockInspection());
  }
}
