package com.siyeh.ig.visibility;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodOverloadsParentMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/visibility/method_overloads_parent_method", new MethodOverloadsParentMethodInspection());
  }
}