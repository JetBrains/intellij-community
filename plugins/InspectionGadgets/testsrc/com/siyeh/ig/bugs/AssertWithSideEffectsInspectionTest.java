package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class AssertWithSideEffectsInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/assert_with_side_effects",
           new AssertWithSideEffectsInspection());
  }
}