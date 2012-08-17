package com.siyeh.ig.initialization;

import com.siyeh.ig.IGInspectionTestCase;

public class InstanceVariableUninitializedUseInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/initialization/instance_variable_uninitialized_use", new InstanceVariableUninitializedUseInspection());
  }
}
