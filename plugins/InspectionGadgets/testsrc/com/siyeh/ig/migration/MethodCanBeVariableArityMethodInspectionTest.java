package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class MethodCanBeVariableArityMethodInspectionTest extends LightJavaInspectionTestCase {

  public void testMethodCanBeVariableArity() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    final MethodCanBeVariableArityMethodInspection inspection = new MethodCanBeVariableArityMethodInspection();
    inspection.ignoreByteAndShortArrayParameters = true;
    inspection.ignoreOverridingMethods = true;
    inspection.onlyReportPublicMethods = true;
    inspection.ignoreMultipleArrayParameters = true;
    return inspection;
  }
}