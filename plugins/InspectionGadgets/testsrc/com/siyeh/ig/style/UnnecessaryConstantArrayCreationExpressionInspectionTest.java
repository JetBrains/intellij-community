package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryConstantArrayCreationExpressionInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryConstantArrayCreationExpression() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryConstantArrayCreationExpressionInspection();
  }
}