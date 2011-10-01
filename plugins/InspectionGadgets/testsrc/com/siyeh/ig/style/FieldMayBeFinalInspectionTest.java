package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class FieldMayBeFinalInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/field_final",
           new FieldMayBeFinalInspection());
  }
}