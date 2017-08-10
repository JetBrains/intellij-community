package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class ExceptionFromCatchWhichDoesntWrapInspectionTest extends
                                                             IGInspectionTestCase {

  public void test() {
    final ExceptionFromCatchWhichDoesntWrapInspection tool = new ExceptionFromCatchWhichDoesntWrapInspection();
    tool.ignoreCantWrap = true;
    doTest("com/siyeh/igtest/errorhandling/exception_from_catch", tool);
  }
}
