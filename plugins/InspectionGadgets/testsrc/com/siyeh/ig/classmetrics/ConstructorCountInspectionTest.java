package com.siyeh.ig.classmetrics;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstructorCountInspectionTest extends IGInspectionTestCase {

  public void test() {
    final ConstructorCountInspection tool = new ConstructorCountInspection();
    tool.m_limit = 2;
    tool.ignoreDeprecatedConstructors = true;
    doTest("com/siyeh/igtest/classmetrics/constructor_count", tool);
  }
}