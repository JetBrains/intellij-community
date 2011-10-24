package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class DoubleNegationInspectionTest
  extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/double_negation", new DoubleNegationInspection());
  }
}