package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConstantValueVariableUseInspectionTest extends LightInspectionTestCase {

  public void testConstantValueVariableUse() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConstantValueVariableUseInspection();
  }
}