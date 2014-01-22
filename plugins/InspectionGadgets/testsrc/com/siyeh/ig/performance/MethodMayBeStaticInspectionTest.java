package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodMayBeStaticInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final MethodMayBeStaticInspection tool = new MethodMayBeStaticInspection();
    tool.m_ignoreEmptyMethods = false;
    tool.m_ignoreDefaultMethods = false;
    doTest("com/siyeh/igtest/performance/method_may_be_static", tool);
  }
}