package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SimplifiableEqualsExpressionInspectionTest extends LightInspectionTestCase {

  public void testSimplifiableEqualsExpression() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableEqualsExpressionInspection();
  }
}
