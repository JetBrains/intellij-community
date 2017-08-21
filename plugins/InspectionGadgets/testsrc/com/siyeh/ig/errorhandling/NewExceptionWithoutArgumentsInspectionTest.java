package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class NewExceptionWithoutArgumentsInspectionTest extends IGInspectionTestCase {

  public void test() {
    final NewExceptionWithoutArgumentsInspection tool = new NewExceptionWithoutArgumentsInspection();
    doTest("com/siyeh/igtest/errorhandling/new_exception_without_arguments", tool);
  }

}
