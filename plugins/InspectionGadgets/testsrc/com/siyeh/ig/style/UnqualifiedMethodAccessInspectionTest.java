package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnqualifiedMethodAccessInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unqualified_method_access", new UnqualifiedMethodAccessInspection());
  }
}