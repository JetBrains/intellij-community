package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class ExceptionNameDoesntEndWithExceptionInspectionTest
  extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/naming/exception_name_doesnt_end_with_exception",
           new ExceptionNameDoesntEndWithExceptionInspection());
  }
}