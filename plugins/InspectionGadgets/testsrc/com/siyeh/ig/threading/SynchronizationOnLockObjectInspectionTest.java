package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class SynchronizationOnLockObjectInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/threading/synchronization_on_lock_object",
           new SynchronizeOnLockInspection());
  }
}