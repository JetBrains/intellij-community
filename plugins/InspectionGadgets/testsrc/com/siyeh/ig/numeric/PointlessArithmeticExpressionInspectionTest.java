package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PointlessArithmeticExpressionInspectionTest extends LightInspectionTestCase {

  public void testPointlessArithmeticExpression() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PointlessArithmeticExpressionInspection();
  }
}