// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.SuspiciousAssignmentOperatorInspection;

public class SuspiciousAssignmentOperatorFixTest extends IGQuickFixesTestCase {

  public void testDivAssignmentOperator() {
    doTest(getTestName(false), CommonQuickFixBundle.message("fix.replace.x.with.y", "/=", "="));
  }

  public void testMultAssignmentOperator() {
    doTest(getTestName(false), CommonQuickFixBundle.message("fix.replace.x.with.y", "*=", "="));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SuspiciousAssignmentOperatorInspection());
  }

  @Override
  protected String getRelativePath() {
    return "bugs/suspicious_assignment_operator";
  }
}
