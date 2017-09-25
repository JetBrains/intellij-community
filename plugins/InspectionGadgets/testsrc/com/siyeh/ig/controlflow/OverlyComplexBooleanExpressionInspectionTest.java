package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class OverlyComplexBooleanExpressionInspectionTest extends IGInspectionTestCase {

  public void test() {
    final OverlyComplexBooleanExpressionInspection tool = new OverlyComplexBooleanExpressionInspection();
    tool.m_limit = 3;
    tool.m_ignorePureConjunctionsDisjunctions = true;
    doTest("com/siyeh/igtest/controlflow/overly_complex_boolean_expression", tool);
  }
}