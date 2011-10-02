package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ThrowableResultOfMethodCallIgnoredInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/throwable_result_of_method_call_ignored",
           new ThrowableResultOfMethodCallIgnoredInspection());
  }
}
