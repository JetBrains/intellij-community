package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class StringBufferReplaceableByStringInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/constant_string_buffer_may_be_string",
           new StringBufferReplaceableByStringInspection());
  }
}