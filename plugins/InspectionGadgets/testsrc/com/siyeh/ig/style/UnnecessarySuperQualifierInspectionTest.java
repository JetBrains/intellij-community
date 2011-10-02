package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessarySuperQualifierInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unnecessary_super_qualifier",
           new UnnecessarySuperQualifierInspection());
  }
}