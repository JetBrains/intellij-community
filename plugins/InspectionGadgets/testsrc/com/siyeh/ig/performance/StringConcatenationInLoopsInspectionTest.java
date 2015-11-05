package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringConcatenationInLoopsInspectionTest extends LightInspectionTestCase {

  public void testStringConcatenationInLoop() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final StringConcatenationInLoopsInspection inspection = new StringConcatenationInLoopsInspection();
    inspection.m_ignoreUnlessAssigned = false;
    return inspection;
  }
}