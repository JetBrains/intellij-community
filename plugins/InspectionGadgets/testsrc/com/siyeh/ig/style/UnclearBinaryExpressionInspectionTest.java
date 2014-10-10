package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnclearBinaryExpressionInspectionTest extends LightInspectionTestCase {

  public void testUnclearBinaryExpression() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnclearBinaryExpressionInspection();
  }
}