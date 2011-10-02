package com.siyeh.ig.dataflow;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstantValueVariableUseInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/dataflow/constant_value_variable_use",
           new ConstantValueVariableUseInspection());
  }
}