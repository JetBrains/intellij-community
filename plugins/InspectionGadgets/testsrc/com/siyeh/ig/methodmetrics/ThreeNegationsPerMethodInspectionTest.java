package com.siyeh.ig.methodmetrics;

import com.siyeh.ig.IGInspectionTestCase;

public class ThreeNegationsPerMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final ThreeNegationsPerMethodInspection tool = new ThreeNegationsPerMethodInspection();
    tool.m_ignoreInEquals = true;
    tool.ignoreInAssert = true;
    doTest("com/siyeh/igtest/methodmetrics/three_negations_per_method", tool);
  }
}