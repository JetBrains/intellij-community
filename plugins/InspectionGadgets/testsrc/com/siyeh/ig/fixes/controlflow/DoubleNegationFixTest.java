// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.DoubleNegationInspection;

/**
 * @author Bas Leijdekkers
 */
public class DoubleNegationFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DoubleNegationInspection());
    myRelativePath = "controlflow/double_negation";
    myDefaultHint = InspectionGadgetsBundle.message("double.negation.quickfix");
  }

  public void testDoubleNegation() {
    doExpressionTest(myDefaultHint,
                     "!/**/!(System.currentTimeMillis() > 1_000_000)",
                     "System.currentTimeMillis() > 1_000_000");
  }

  public void testDoubleDoubleNegation() { doTest(); }

  public void testPolyadicParentheses() { doTest(); }

  public void testDoNotCleanupWithSingleNegation() {
    assertQuickfixNotAvailable(myDefaultHint,
                               "class X {\n" +
                               "  boolean test(boolean isValid) {\n" +
                               "    return !/**/isValid;\n" +
                               "  }\n" +
                               "}\n");
  }
}
