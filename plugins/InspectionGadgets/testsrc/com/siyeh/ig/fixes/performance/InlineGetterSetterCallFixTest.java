// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.CallToSimpleGetterInClassInspection;
import com.siyeh.ig.performance.CallToSimpleSetterInClassInspection;

public class InlineGetterSetterCallFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CallToSimpleSetterInClassInspection());
    myFixture.enableInspections(new CallToSimpleGetterInClassInspection());
    myRelativePath = "performance/simple_setter";
  }

  public void testParentheses() { doTest(InspectionGadgetsBundle.message("call.to.simple.setter.in.class.inline.quickfix")); }
  public void testQualifiedCall() { doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  public void testSimpleField() { doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  public void testNameClash() { doTest(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
  public void testTopLevelGetterCall() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix")); }
}