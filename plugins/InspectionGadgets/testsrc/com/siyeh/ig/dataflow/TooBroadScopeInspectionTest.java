package com.siyeh.ig.dataflow;

import com.siyeh.ig.IGInspectionTestCase;

public class TooBroadScopeInspectionTest extends IGInspectionTestCase {
  public void testTooBroadScope() {
    final TooBroadScopeInspection inspection = new TooBroadScopeInspection();
    doTest("com/siyeh/igtest/dataflow/scope", inspection);
  }
}