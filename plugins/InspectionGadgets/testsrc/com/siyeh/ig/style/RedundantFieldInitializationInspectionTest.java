package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class RedundantFieldInitializationInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/redundant_field_initialization", new RedundantFieldInitializationInspection());
  }
}