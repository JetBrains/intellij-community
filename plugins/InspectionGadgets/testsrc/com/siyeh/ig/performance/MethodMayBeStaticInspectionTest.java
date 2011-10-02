package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodMayBeStaticInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/method_may_be_static",
           new MethodMayBeStaticInspection());
  }
}