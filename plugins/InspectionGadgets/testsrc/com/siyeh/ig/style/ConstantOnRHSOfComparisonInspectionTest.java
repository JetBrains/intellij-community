package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstantOnRHSOfComparisonInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/style/constant_on_rhs", new ConstantOnRHSOfComparisonInspection());
  }
}