package com.siyeh.ig.numeric;

import com.siyeh.ig.IGInspectionTestCase;

public class CastThatLosesPrecisionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/numeric/cast_that_loses_precision", new CastThatLosesPrecisionInspection());
  }
}