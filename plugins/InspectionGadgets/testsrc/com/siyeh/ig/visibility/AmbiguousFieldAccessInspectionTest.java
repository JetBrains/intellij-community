package com.siyeh.ig.visibility;

import com.siyeh.ig.IGInspectionTestCase;

public class AmbiguousFieldAccessInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/visibility/ambiguous_field_access", new AmbiguousFieldAccessInspection());
  }
}