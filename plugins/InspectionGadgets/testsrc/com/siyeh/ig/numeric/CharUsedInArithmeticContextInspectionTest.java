package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class CharUsedInArithmeticContextInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/numeric/char_context",
           new CharUsedInArithmeticContextInspection());
  }
}