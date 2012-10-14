package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationInFormatCallInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/string_concatenation_in_format_call", new StringConcatenationInFormatCallInspection());
  }
}