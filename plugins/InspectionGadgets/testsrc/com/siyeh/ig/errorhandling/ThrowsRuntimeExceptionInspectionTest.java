package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class ThrowsRuntimeExceptionInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/errorhandling/throws_runtime_exception", new ThrowsRuntimeExceptionInspection());
  }
}
