package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class MismatchedStringBuilderQueryUpdateInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/mismatched_string_builder_query_update", new MismatchedStringBuilderQueryUpdateInspection());
  }
}