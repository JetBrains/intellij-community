// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.DefaultNotLastCaseInSwitchInspection;

public class MakeDefaultCaseLastFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DefaultNotLastCaseInSwitchInspection());
    myRelativePath = "controlflow/makeDefaultLast";
    myDefaultHint = "Make 'default' the last case";
  }

  public void testLabeledSwitchRule() { doTest(); }
  public void testOldStyleSwitchStatement() { doTest(); }
  public void testOldStyleSwitchStatementNoBreakThrough() { assertQuickfixNotAvailable(); }
  public void testOldStyleSwitchStatementNoBreakThrough1() { assertQuickfixNotAvailable(); }
}