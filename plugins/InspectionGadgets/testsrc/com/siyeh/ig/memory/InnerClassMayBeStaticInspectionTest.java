package com.siyeh.ig.memory;

import com.siyeh.ig.IGInspectionTestCase;

public class InnerClassMayBeStaticInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/memory/inner_class_may_be_static",
           new InnerClassMayBeStaticInspection());
  }
}