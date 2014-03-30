package com.siyeh.ig.assignment;

import com.siyeh.ig.IGInspectionTestCase;

public class AssignmentUsedAsConditionInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/assignment/assignment_used_as_condition", new AssignmentUsedAsConditionInspection());
  }
}
