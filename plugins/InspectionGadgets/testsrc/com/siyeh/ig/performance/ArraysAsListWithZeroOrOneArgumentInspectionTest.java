package com.siyeh.ig.performance;

import com.siyeh.ig.IGInspectionTestCase;

public class ArraysAsListWithZeroOrOneArgumentInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/performance/arrays_as_list_with_one_argument", new ArraysAsListWithZeroOrOneArgumentInspection());
  }
}