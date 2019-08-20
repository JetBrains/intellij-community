// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void testPolyadic2() { doTest(); }
  public void testPolyadic3() { doTest(); }
  public void testPolyadic4() { doTest(); }
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
  public void testDivision2() { doTest(); }
  public void testSwitchExpression() { doTest(); }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryParenthesesInspection();
  }
}