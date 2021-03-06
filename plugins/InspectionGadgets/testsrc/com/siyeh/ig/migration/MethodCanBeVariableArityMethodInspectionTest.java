package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MethodCanBeVariableArityMethodInspectionTest extends LightJavaInspectionTestCase {

  public void testMethodCanBeVariableArity() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
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