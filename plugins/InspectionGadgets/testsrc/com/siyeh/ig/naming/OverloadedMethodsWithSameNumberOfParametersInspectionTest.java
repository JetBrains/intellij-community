package com.siyeh.ig.naming;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGInspectionTestCase;

public class OverloadedMethodsWithSameNumberOfParametersInspectionTest extends IGInspectionTestCase {

  public void testIgnoreInconvertibleTypes() {
    doTest(new OverloadedMethodsWithSameNumberOfParametersInspection());
  }

  public void testReportAll() {
    final OverloadedMethodsWithSameNumberOfParametersInspection inspection = new OverloadedMethodsWithSameNumberOfParametersInspection();
    inspection.ignoreInconvertibleTypes = false;
    doTest(inspection);
  }

  private void doTest(BaseInspection inspection) {
    doTest("com/siyeh/igtest/naming/overloaded_methods_with_same_number_of_parameters/" + getTestName(false), inspection);
  }
}