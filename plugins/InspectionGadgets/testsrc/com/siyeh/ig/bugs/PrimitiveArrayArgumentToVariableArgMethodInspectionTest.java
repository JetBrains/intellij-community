package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class PrimitiveArrayArgumentToVariableArgMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/var_arg", new PrimitiveArrayArgumentToVariableArgMethodInspection());
  }
}