package com.siyeh.ig.visibility;

import com.siyeh.ig.IGInspectionTestCase;

public class AmbiguousMethodCallInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/visibility/ambiguous", new AmbiguousMethodCallInspection());
  }
}