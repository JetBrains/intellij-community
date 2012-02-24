package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationInsideStringBufferAppendInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/string_concatenation_inside_string_buffer_append",
           new StringConcatenationInsideStringBufferAppendInspection());
  }
}