/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ipp.parenthesis;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnclearBinaryExpressionInspection;

/**
 * @author Bas Leijdekkers
 */
public class AddClarifyingParenthesesIntentionTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnclearBinaryExpressionInspection();
  }

  public void testCommentsInAssignment() { doTest(); }
  public void testCommentsInConditional() { doTest(); }
  public void testCommentsInInstanceof() { doTest(); }
  public void testCommentsInParentheses() { doTest(); }
  public void testCommentsInPolyadicExpression() { doTest(); }
  public void testNotTooManyParentheses() { doTest(); }
  public void testSimpleAssignment() { assertQuickfixNotAvailable(); }
  public void testUnclearAssignment() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "style/add_clarifying_parentheses";
  }
}