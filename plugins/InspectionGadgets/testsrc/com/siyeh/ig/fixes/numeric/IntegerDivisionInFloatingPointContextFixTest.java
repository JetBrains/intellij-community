// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.IntegerDivisionInFloatingPointContextInspection;

public class IntegerDivisionInFloatingPointContextFixTest extends IGQuickFixesTestCase {

  public void testSimpleFloat() { doTest(); }
  public void testSimpleDouble() { doTest(); }
  public void testExpectedParenthesizedExpr() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IntegerDivisionInFloatingPointContextInspection inspection = new IntegerDivisionInFloatingPointContextInspection();
    myFixture.enableInspections(inspection);
    myDefaultHint = InspectionGadgetsBundle.message("integer.division.in.floating.point.context.quickfix");
  }

  @Override
  protected String getRelativePath() {
    return "numeric/integer_division_in_floating_point_context";
  }
}
