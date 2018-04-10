/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.IfMayBeConditionalInspection;

public class IfMayBeConditionalFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new IfMayBeConditionalInspection());
    myRelativePath = "controlflow/if_conditional";
    myDefaultHint = InspectionGadgetsBundle.message("if.may.be.conditional.quickfix");
  }

  public void testComment() { doTest(); }
}