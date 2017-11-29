package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class KeySetIterationMayUseEntrySetInspectionTest extends LightInspectionTestCase {

  public void testKeySetIterationMayUseEntrySet() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new KeySetIterationMayUseEntrySetInspection();
  }
}