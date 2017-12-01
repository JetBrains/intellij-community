package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class ForLoopReplaceableByWhileInspectionTest extends IGInspectionTestCase {

  public void test() {
    ForLoopReplaceableByWhileInspection inspection = new ForLoopReplaceableByWhileInspection();
    inspection.m_ignoreLoopsWithBody = true;
    doTest("com/siyeh/igtest/controlflow/for_loop_replaceable_by_while", inspection);
  }
}