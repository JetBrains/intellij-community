package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class VariableNotUsedInsideIfInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/variable_not_used_inside_if", new VariableNotUsedInsideIfInspection());
  }
}
