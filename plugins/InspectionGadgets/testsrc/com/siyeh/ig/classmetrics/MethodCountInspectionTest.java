package com.siyeh.ig.classmetrics;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodCountInspectionTest extends IGInspectionTestCase {

  public void test() {
    final MethodCountInspection tool = new MethodCountInspection();
    tool.m_limit = 5;
    tool.ignoreOverridingMethods = true;
    doTest("com/siyeh/igtest/classmetrics/method_count", tool);
  }
}