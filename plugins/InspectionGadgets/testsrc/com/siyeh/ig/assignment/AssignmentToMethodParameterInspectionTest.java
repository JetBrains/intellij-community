package com.siyeh.ig.assignment;

import com.siyeh.ig.IGInspectionTestCase;

public class AssignmentToMethodParameterInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final AssignmentToMethodParameterInspection inspection =
      new AssignmentToMethodParameterInspection();
    inspection.ignoreTransformationOfOriginalParameter = true;
    doTest("com/siyeh/igtest/assignment/method_parameter",
           inspection);
  }
}