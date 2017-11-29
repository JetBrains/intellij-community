package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class UpperCaseFieldNameNotConstantInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/naming/upper_case_field_name_not_constant",
           new UpperCaseFieldNameNotConstantInspection());
  }
}