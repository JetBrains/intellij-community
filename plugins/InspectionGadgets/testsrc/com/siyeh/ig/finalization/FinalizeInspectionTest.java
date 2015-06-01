package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class FinalizeInspectionTest extends LightInspectionTestCase {

  public void testFinalize() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FinalizeInspection();
  }
}