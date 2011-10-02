package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

public class InstanceofChainInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/abstraction/instanceof_chain",
           new InstanceofChainInspection());
  }
}