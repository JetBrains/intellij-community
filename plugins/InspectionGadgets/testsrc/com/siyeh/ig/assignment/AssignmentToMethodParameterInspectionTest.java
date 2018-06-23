package com.siyeh.ig.assignment;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author bas
 */
public class AssignmentToMethodParameterInspectionTest extends LightInspectionTestCase {

  public void testAssignmentToMethodParameterMissesCompoundAssign() {
    myFixture.enableInspections(new AssignmentToMethodParameterInspection());
    doTest();
  }

  public void testIgnoreTransformationOfParameter() {
    final AssignmentToMethodParameterInspection inspection = new AssignmentToMethodParameterInspection();
    inspection.ignoreTransformationOfOriginalParameter = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return null;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/assignment/method_parameter";
  }
}