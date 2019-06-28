package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class MismatchedStringBuilderQueryUpdateInspectionTest extends LightJavaInspectionTestCase {

  public void testMismatchedStringBuilderQueryUpdate() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MismatchedStringBuilderQueryUpdateInspection();
  }
}