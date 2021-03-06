package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class DuplicateConditionInspectionTest extends LightJavaInspectionTestCase {

  public void testDuplicateCondition() {
    doTest();
  }
  public void testDuplicateConditionNoSideEffect() {
    doTest();
  }
  public void testDuplicateBooleanBranch() {
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