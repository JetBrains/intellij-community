package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstantAssertConditionInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/bugs/constant_assert_condition",
           new ConstantAssertConditionInspection());
  }
}