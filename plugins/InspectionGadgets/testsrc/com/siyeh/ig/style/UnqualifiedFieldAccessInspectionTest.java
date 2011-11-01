package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnqualifiedFieldAccessInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unqualified_field_access", new UnqualifiedFieldAccessInspection());
  }
}