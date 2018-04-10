package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class DuplicateConditionInspectionTest extends LightInspectionTestCase {

  public void testDuplicateCondition() {
    doTest();
  }
  public void testDuplicateConditionNoSideEffect() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    DuplicateConditionInspection inspection = new DuplicateConditionInspection();
    inspection.ignoreSideEffectConditions = getTestName(false).contains("NoSideEffect");
    return inspection;
  }
}