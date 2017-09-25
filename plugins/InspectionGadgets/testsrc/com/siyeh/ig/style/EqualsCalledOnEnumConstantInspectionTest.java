package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class EqualsCalledOnEnumConstantInspectionTest extends LightInspectionTestCase {

  public void testEqualsCalled() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EqualsCalledOnEnumConstantInspection();
  }
}