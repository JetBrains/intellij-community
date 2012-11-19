package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class StringBufferReplaceableByStringBuilderInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/string_buffer_replaceable_by_string_builder",
           new StringBufferReplaceableByStringBuilderInspection());
  }
}