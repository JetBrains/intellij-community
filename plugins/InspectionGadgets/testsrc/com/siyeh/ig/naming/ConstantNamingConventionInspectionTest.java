package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstantNamingConventionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/naming/constant_naming_convention", new ConstantNamingConventionInspection());
  }
}