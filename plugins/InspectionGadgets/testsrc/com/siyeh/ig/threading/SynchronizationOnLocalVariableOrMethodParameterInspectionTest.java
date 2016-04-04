package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SynchronizationOnLocalVariableOrMethodParameterInspectionTest extends LightInspectionTestCase {

  public void testSynchronizationOnLocalVariableOrMethodParameter() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SynchronizationOnLocalVariableOrMethodParameterInspection();
  }
}
