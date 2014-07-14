package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class MismatchedStringBuilderQueryUpdateInspectionTest extends LightInspectionTestCase {

  public void testMismatchedStringBuilderQueryUpdate() throws Exception {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MismatchedStringBuilderQueryUpdateInspection();
  }
}