package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class StandardVariableNamesInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    final StandardVariableNamesInspection inspection = new StandardVariableNamesInspection();
    inspection.ignoreParameterNameSameAsSuper = true;
    doTest("com/siyeh/igtest/naming/standard_variable_names", inspection);
  }
}