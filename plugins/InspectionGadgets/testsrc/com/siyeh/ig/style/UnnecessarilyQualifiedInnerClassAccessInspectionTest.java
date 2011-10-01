package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessarilyQualifiedInnerClassAccessInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unnecessarily_qualified_inner_class_access",
           new UnnecessarilyQualifiedInnerClassAccessInspection());
  }
}