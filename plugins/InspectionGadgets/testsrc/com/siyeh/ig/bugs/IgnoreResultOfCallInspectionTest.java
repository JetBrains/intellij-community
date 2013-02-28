package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class IgnoreResultOfCallInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/ignore_result_of_call", new IgnoreResultOfCallInspection());
  }
}