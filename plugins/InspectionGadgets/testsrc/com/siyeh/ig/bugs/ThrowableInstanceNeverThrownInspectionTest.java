package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ThrowableInstanceNeverThrownInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/throwable_instance_never_thrown",
           new ThrowableInstanceNeverThrownInspection());
  }
}
