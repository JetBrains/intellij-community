package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class CaughtExceptionImmediatelyRethrownInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/errorhandling/caught_exception_immediately_rethrown", new CaughtExceptionImmediatelyRethrownInspection());
  }
}
