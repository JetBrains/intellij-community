package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AutoUnboxingInspectionTest extends LightInspectionTestCase {

  public void testAutoUnboxing() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AutoUnboxingInspection();
  }
}