// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.siyeh.ig.fixes.dataflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.dataflow.ReuseOfLocalVariableInspection;

public class ReuseOfLocalVariableInspectionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ReuseOfLocalVariableInspection());
    myRelativePath = "dataflow/reuse_of_local";
  }

  public void testSimple() { doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
  public void testParentheses() { doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
}
