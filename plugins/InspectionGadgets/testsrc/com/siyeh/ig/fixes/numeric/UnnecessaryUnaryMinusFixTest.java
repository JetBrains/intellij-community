// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.UnnecessaryUnaryMinusInspection;

public class UnnecessaryUnaryMinusFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    UnnecessaryUnaryMinusInspection inspection = new UnnecessaryUnaryMinusInspection();
    myFixture.enableInspections(inspection);
    myRelativePath = "numeric/unnecessary_unary_minus";
  }

  public void testUnaryMinus() {
    doTest("Fix all 'Unnecessary unary minus' problems in file");
  }

  public void testFinalVariable() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  public void testLocalVariableRemoveMinus() {
    doTest(InspectionGadgetsBundle.message("unnecessary.unary.minus.remove.quickfix", "i"));
  }

  public void testLocalVariableDecrement() {
    doTest(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  public void testMethodCall() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }

  public void testCommentBetweenOperatorAndOperand() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("convert.double.unary.quickfix", "--", "i"));
  }
}
