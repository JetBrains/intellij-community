package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessarilyQualifiedStaticUsageInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/style/unnecessarily_qualified_static_usage",
           new UnnecessarilyQualifiedStaticUsageInspection());
  }
}