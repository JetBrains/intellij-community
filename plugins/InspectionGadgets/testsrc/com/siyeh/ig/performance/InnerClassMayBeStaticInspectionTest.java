package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class InnerClassMayBeStaticInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/inner_class_may_be_static",
           new InnerClassMayBeStaticInspection());
  }
}