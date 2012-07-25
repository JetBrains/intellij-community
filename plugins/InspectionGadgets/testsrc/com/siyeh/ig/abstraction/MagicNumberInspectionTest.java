package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

public class MagicNumberInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final MagicNumberInspection tool = new MagicNumberInspection();
    tool.ignoreInHashCode = true;
    tool.ignoreInAnnotations = true;
    doTest("com/siyeh/igtest/abstraction/magic_number", tool);
  }
}