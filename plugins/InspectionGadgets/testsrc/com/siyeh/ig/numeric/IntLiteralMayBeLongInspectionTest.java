package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class IntLiteralMayBeLongInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/int_literal_may_be_long",
           new IntLiteralMayBeLongLiteralInspection());
  }
}