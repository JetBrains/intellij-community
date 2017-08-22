package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class ForLoopReplaceableByWhileInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/controlflow/for_loop_replaceable_by_while",
           new ForLoopReplaceableByWhileInspection());
  }
}