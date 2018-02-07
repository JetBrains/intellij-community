/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
}