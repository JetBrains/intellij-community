package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodCanBeVariableArityMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final MethodCanBeVariableArityMethodInspection tool = new MethodCanBeVariableArityMethodInspection();
    tool.ignoreByteAndShortArrayParameters = true;
    tool.ignoreOverridingMethods = true;
    doTest("com/siyeh/igtest/migration/method_can_be_variable_arity_method", tool);
  }
}