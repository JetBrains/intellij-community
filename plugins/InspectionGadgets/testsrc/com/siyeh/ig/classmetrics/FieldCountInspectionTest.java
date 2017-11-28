package com.siyeh.ig.classmetrics;

import com.siyeh.ig.IGInspectionTestCase;

public class FieldCountInspectionTest extends IGInspectionTestCase {

  public void test() {
    final FieldCountInspection tool = new FieldCountInspection();
    tool.m_limit = 5;
    tool.myCountEnumConstants = false;
    doTest("com/siyeh/igtest/classmetrics/field_count", tool);
  }
}