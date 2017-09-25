package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ReturnFromFinallyBlockInspectionTest extends LightInspectionTestCase {

  public void testReturnFromFinallyBlock() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ReturnFromFinallyBlockInspection();
  }
}
