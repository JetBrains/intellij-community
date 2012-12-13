package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class EnumeratedConstantNamingConventionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/naming/enumerated_constant_naming_convention", new EnumeratedConstantNamingConventionInspection());
  }
}