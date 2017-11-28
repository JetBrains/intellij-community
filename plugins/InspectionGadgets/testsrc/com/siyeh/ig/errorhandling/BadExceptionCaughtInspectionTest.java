package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class BadExceptionCaughtInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/errorhandling/bad_exception_caught", new BadExceptionCaughtInspection());
  }
}
