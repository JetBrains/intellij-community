package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConfusingFloatingPointLiteralInspectionTest extends LightInspectionTestCase {

  public void testConfusingFloatingPointLiteral() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConfusingFloatingPointLiteralInspection();
  }
}