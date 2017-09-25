package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class LengthOneStringsInConcatenationInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/performance/length_one_strings_in_concatenation",
           new LengthOneStringsInConcatenationInspection());
  }
}