package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class ReturnOfDateFieldInspectionTest extends IGInspectionTestCase {

  public void test() {
    final ReturnOfDateFieldInspection tool = new ReturnOfDateFieldInspection();
    tool.ignorePrivateMethods = true;
    doTest("com/siyeh/igtest/encapsulation/return_of_date_field", tool);
  }
}