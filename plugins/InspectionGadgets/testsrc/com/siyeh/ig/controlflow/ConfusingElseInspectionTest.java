package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class ConfusingElseInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/confusing_else",
           new ConfusingElseInspection());
  }
}