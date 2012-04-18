package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class MathRandomCastToIntInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/math_random_cast_to_int", new MathRandomCastToIntInspection());
  }
}
