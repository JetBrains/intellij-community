package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class NonShortCircuitBooleanInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/bugs/non_short_circuit_boolean", new NonShortCircuitBooleanInspection());
  }
}