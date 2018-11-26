// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.TailRecursionInspection;

/**
 * @author Bas Leijdekkers
 */
public class TailRecursionInspectionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TailRecursionInspection());
    myRelativePath = "performance/tail_recursion";
    myDefaultHint = InspectionGadgetsBundle.message("tail.recursion.replace.quickfix");
  }

  public void testCallOnOtherInstance1() { doTest(); }
  public void testCallOnOtherInstance2() { doTest(); }
  public void testDependency1() { doTest(); }
  public void testDependency2() { doTest(); }
  public void testDependency3() { doTest(); }
  public void testDependency4() { doTest(); }
  public void testThisVariable() { doTest(); }
  public void testUnmodifiedParameter() { doTest(); }
  public void testRemoveEmptyElse() { doTest(); }
  public void testRemoveEmptyElseCommentAtLineStart() { doTest(); }
}
