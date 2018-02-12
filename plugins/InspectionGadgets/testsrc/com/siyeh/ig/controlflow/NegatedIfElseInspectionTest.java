package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class NegatedIfElseInspectionTest extends IGInspectionTestCase {

  public void test() {
    final NegatedIfElseInspection tool = new NegatedIfElseInspection();
    tool.m_ignoreNegatedNullComparison = true;
    tool.m_ignoreNegatedZeroComparison = true;
    doTest("com/siyeh/igtest/controlflow/negated_if_else", tool);
  }
}