/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.parenthesis;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryParenthesesInspection;

public class UnnecessaryParenthesesQuickFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = InspectionGadgetsBundle.message("unnecessary.parentheses.remove.quickfix");
    myRelativePath = "parentheses";
  }

  public void testPolyadic() { doTest(); }
  public void testCommutative() { doTest(); }
  public void testWrapping() { doTest(); }
  public void testNotCommutative() { assertQuickfixNotAvailable(); }
  public void testStringParentheses() { assertQuickfixNotAvailable(); }
  public void testComparisonParentheses() { assertQuickfixNotAvailable(); }
  public void testNotCommutative2() { doTest(); }
  public void testArrayInitializer() { doTest(); }
  public void testArrayAccessExpression() { doTest(); }
  public void testArrayAccessExpression2() { doTest(); }
  public void testSimplePrecedence() { assertQuickfixNotAvailable(); }
  public void testLambdaQualifier() { assertQuickfixNotAvailable(); }
  public void testLambdaInTernary() { doTest(); }
  public void testLambdaCast() { doTest(); }
  public void testLambdaBody() { doTest(); }
  public void testDivision() { doTest(); }
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryParenthesesInspection();
  }
}