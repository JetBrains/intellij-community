// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;

public class AddThisQualifierFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnqualifiedFieldAccessInspection());
    myDefaultHint = InspectionGadgetsBundle.message("add.this.qualifier.quickfix");
  }

  @Override
  protected String getRelativePath() {
    return "style/add_this_qualifier";
  }

  public void testSimple() {
    doTest();
  }

  public void testStaticClass() {
    doTest();
  }
}
