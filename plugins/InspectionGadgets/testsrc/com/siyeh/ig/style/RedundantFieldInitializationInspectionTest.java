package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RedundantFieldInitializationInspectionTest extends LightInspectionTestCase {

  public void testRedundantFieldInitialization() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantFieldInitializationInspection();
  }
}