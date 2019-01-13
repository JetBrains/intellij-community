// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.jdk;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.jdk.AutoUnboxingInspection;

public class AutoUnboxingExplicitFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new AutoUnboxingInspection());
    myRelativePath = "jdk/auto_unboxing";
    myDefaultHint = InspectionGadgetsBundle.message("auto.unboxing.make.unboxing.explicit.quickfix");
  }

  public void testCommentsInTypeCast() { doTest(); }
  public void testSwitchExpressionPrecedence() { doTest(); }
  public void testMethodCallPrecedence() { doTest(); }
  public void testExplicitTypeArguments() { doTest(); }
  public void testExplicitTypeArguments2() { doTest(); }
}