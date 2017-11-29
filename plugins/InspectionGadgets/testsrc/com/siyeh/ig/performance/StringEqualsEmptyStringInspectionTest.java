package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class StringEqualsEmptyStringInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/performance/string_equals_empty_string",
           new StringEqualsEmptyStringInspection());
  }
}