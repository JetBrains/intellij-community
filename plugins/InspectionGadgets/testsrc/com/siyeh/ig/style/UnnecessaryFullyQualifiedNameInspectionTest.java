package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessaryFullyQualifiedNameInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/unnecessary_fully_qualified_name",
           new UnnecessaryFullyQualifiedNameInspection());
  }
}
