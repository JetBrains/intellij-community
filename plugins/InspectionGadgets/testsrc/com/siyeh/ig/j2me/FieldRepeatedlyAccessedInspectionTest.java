package com.siyeh.ig.j2me;

import com.siyeh.ig.IGInspectionTestCase;

public class FieldRepeatedlyAccessedInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/j2me/field_repeatedly_accessed",
           new FieldRepeatedlyAccessedInspection());
  }
}
