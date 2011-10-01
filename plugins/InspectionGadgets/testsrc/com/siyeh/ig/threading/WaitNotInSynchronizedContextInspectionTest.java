package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class WaitNotInSynchronizedContextInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/threading/wait_not_in_synchronized_context",
           new WaitNotInSynchronizedContextInspection());
  }
}