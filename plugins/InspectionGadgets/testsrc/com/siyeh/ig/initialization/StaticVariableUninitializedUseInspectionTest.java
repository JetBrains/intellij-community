package com.siyeh.ig.initialization;

import com.siyeh.ig.IGInspectionTestCase;

public class StaticVariableUninitializedUseInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/initialization/static_variable_uninitialized_use",
           new StaticVariableUninitializedUseInspection());
  }
}
