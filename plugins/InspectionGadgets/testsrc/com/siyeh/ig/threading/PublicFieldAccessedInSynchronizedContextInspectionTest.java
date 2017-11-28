package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class PublicFieldAccessedInSynchronizedContextInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/threading/public_field_accessed_in_synchronized_context",
           new PublicFieldAccessedInSynchronizedContextInspection());
  }
}