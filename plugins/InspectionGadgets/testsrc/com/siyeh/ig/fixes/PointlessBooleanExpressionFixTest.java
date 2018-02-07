/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.PointlessBooleanExpressionInspection;

public class PointlessBooleanExpressionFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PointlessBooleanExpressionInspection());
    myRelativePath = "pointlessboolean";
    myDefaultHint = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
  }

  public void testNegation() { doTest(); }
  public void testPolyadic() { doTest(); }
  public void testBoxed() { doTest(); }
  public void testSideEffects() { doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  public void testSideEffectsField() { doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")); }
  public void testCompoundAssignment1() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  public void testCompoundAssignment2() { doTest(); }
  public void testCompoundAssignment3() { doTest(); }
  public void testCompoundAssignmentSideEffect() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
  public void testCompoundAssignmentSideEffect2() { doTest(); }
  public void testCompoundAssignmentSideEffect3() { doTest(InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix")); }
}
