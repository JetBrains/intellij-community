package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnclearBinaryExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testUnclearBinaryExpression() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnclearBinaryExpressionInspection();
  }
}