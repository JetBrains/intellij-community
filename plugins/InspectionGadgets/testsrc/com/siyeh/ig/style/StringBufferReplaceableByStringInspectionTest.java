package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class StringBufferReplaceableByStringInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/string_buffer_replaceable_by_string",
           new StringBufferReplaceableByStringInspection());
  }
}