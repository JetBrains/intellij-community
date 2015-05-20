package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class ThrowableResultOfMethodCallIgnoredInspectionTest extends LightInspectionTestCase {

  public void testThrowableResultOfMethodCallIgnored() throws Exception {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowableResultOfMethodCallIgnoredInspection();
  }
}
