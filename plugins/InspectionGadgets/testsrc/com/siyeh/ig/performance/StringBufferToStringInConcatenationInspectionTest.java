package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class StringBufferToStringInConcatenationInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/string_buffer_to_string_in_concatenation",
           new StringBufferToStringInConcatenationInspection());
  }
}