package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class AbstractMethodOverridesAbstractMethodInspectionTest extends IGInspectionTestCase {

  public void test() {
    final AbstractMethodOverridesAbstractMethodInspection tool = new AbstractMethodOverridesAbstractMethodInspection();
    tool.ignoreAnnotations = true;
    tool.ignoreJavaDoc = true;
    doTest("com/siyeh/igtest/inheritance/abstract_method_overrides_abstract_method", tool);
  }
}
