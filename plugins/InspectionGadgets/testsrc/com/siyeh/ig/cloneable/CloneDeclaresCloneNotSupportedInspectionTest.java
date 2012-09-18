package com.siyeh.ig.cloneable;

import com.siyeh.ig.IGInspectionTestCase;

public class CloneDeclaresCloneNotSupportedInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/cloneable/clone_declares_clone_not_supported", new CloneDeclaresCloneNotSupportedInspection());
  }
}