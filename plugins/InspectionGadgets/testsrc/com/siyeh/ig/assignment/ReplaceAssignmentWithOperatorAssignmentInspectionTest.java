package com.siyeh.ig.assignment;

import com.siyeh.ig.IGInspectionTestCase;

public class ReplaceAssignmentWithOperatorAssignmentInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/assignment/replace_assignment_with_operator_assignment",
           new ReplaceAssignmentWithOperatorAssignmentInspection());
  }
}