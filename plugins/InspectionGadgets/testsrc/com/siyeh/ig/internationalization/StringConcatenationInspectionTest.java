package com.siyeh.ig.internationalization;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationInspectionTest extends IGInspectionTestCase {

  public void test() {
    final StringConcatenationInspection concatenationInspection = new StringConcatenationInspection();
    concatenationInspection.ignoreThrowableArguments = true;
    doTest("com/siyeh/igtest/internationalization/string_concatenation",
           concatenationInspection);
  }
}