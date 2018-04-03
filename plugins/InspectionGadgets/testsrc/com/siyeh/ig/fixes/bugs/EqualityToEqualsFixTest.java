/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes.bugs;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.NumberEqualityInspection;
import com.siyeh.ig.bugs.ObjectEqualityInspection;

/**
 * @author Bas Leijdekkers
 */
public class EqualityToEqualsFixTest extends IGQuickFixesTestCase {

  public void testSimple() { doTest(InspectionGadgetsBundle.message("equality.to.equals.quickfix")); }
  public void testPrecedence() { doTest(InspectionGadgetsBundle.message("equality.to.equals.quickfix")); }
  public void testNegated() { doTest(InspectionGadgetsBundle.message("inequality.to.not.equals.quickfix")); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ObjectEqualityInspection());
    myFixture.enableInspections(new NumberEqualityInspection());
    myRelativePath = "bugs/equality_to_equals";
  }
}
