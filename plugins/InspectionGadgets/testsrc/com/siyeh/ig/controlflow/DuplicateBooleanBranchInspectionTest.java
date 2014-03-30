package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class DuplicateBooleanBranchInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/duplicate_boolean_branch", new DuplicateBooleanBranchInspection());
  }
}