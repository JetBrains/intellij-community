package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationMissingWhitespaceInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/bugs/string_concatenation_missing_whitespace", new StringConcatenationMissingWhitespaceInspection());
  }
}