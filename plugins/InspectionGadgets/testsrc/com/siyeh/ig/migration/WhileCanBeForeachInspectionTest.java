package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;

public class WhileCanBeForeachInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/while_can_be_foreach",
           new WhileCanBeForeachInspection());
  }
}
