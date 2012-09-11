package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

public class CastToConcreteClassInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final CastToConcreteClassInspection tool = new CastToConcreteClassInspection();
    tool.ignoreInEquals = true;
    doTest("com/siyeh/igtest/abstraction/cast_to_concrete_class", tool);
  }
}