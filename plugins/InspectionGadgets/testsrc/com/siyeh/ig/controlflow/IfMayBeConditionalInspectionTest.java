package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class IfMayBeConditionalInspectionTest extends LightJavaInspectionTestCase {

  public void testIfMayBeConditional() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final IfMayBeConditionalInspection inspection = new IfMayBeConditionalInspection();
    inspection.reportMethodCalls = true;
    return inspection;
  }
}