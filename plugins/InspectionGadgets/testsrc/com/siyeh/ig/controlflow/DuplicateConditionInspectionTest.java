package com.siyeh.ig.controlflow;

import com.siyeh.ig.IGInspectionTestCase;

public class DuplicateConditionInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/controlflow/duplicate_condition", new DuplicateConditionInspection());
  }
}