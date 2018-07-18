// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ConditionalExpressionInspection;

public class ConditionalExpressionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ConditionalExpressionInspection());
    myRelativePath = "controlflow/conditional_expression";
    myDefaultHint = InspectionGadgetsBundle.message("conditional.expression.quickfix");
  }

  public void testThisCall() { assertQuickfixNotAvailable(); }
  public void testBrokenCode() { assertQuickfixNotAvailable(); }
  public void testField() { assertQuickfixNotAvailable(); }

  public void testArrayInitializer() { doTest(); }
  public void testCastNeeded() { doTest(); }
  public void testComment() { doTest(); }
  public void testCommentWithDeclaration() { doTest(); }
  public void testConditionalAsArgument() { doTest(); }
  public void testConditionalInBinaryExpression() { doTest(); }
  public void testConditionalInIf() { doTest(); }
  public void testInsideExprLambda() { doTest(); }
  public void testInsideExprLambdaWithParams() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testNestedConditional() { doTest(); }
  public void testNestedConditionalChangesSemantics() { doTest(InspectionGadgetsBundle.message("conditional.expression.semantics.quickfix")); }
}