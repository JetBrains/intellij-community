package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnqualifiedInnerClassAccessInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    final UnqualifiedInnerClassAccessInspection tool = new UnqualifiedInnerClassAccessInspection();
    tool.ignoreReferencesToLocalInnerClasses = true;
    doTest("com/siyeh/igtest/style/unqualified_inner_class_access", tool);
  }
}