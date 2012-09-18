package com.siyeh.ig.naming;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGInspectionTestCase;

public class OverloadedMethodsWithSameNumberOfParametersInspectionTest extends IGInspectionTestCase {

  public void testIgnoreInconvertibleTypes() throws Exception {
    doTest(new OverloadedMethodsWithSameNumberOfParametersInspection());
  }

  public void testReportAll() throws Exception {
    final OverloadedMethodsWithSameNumberOfParametersInspection inspection = new OverloadedMethodsWithSameNumberOfParametersInspection();
    inspection.ignoreInconvertibleTypes = false;
    doTest(inspection);
  }

  private void doTest(BaseInspection inspection) throws Exception {
    doTest("com/siyeh/igtest/naming/overloaded_methods_with_same_number_of_parameters/" + getTestName(false), inspection);
  }
}