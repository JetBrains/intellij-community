package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

public class OverlyStrongTypeCastInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final OverlyStrongTypeCastInspection tool = new OverlyStrongTypeCastInspection();
    tool.ignoreInMatchingInstanceof = true;
    doTest("com/siyeh/igtest/abstraction/overly_strong_type_cast", tool);
  }
}