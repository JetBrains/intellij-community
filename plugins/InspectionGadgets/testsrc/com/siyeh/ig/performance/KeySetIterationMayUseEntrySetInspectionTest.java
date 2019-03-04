package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KeySetIterationMayUseEntrySetInspectionTest extends LightInspectionTestCase {

  public void testKeySetIterationMayUseEntrySet() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new KeySetIterationMayUseEntrySetInspection();
  }
}