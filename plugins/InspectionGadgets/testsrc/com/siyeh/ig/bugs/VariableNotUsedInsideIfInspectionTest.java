package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class VariableNotUsedInsideIfInspectionTest extends LightInspectionTestCase {

  public void testVariableNotUsedInsideIf() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new VariableNotUsedInsideIfInspection();
  }
}
