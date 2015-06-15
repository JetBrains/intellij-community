package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class FinalizeCallsSuperFinalizeInspectionTest extends LightInspectionTestCase {

  public void testFinalizeCallsSuperFinalize() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FinalizeCallsSuperFinalizeInspection();
  }
}