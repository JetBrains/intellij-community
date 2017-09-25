package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class ExtendsUtilityClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    final ExtendsUtilityClassInspection tool = new ExtendsUtilityClassInspection();
    tool.ignoreUtilityClasses = true;
    doTest("com/siyeh/igtest/inheritance/extends_utility_class", tool);
  }
}
