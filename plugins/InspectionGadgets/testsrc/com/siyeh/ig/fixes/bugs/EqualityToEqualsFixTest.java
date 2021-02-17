// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.NumberEqualityInspection;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.fixes.EqualityToEqualsFix;

/**
 * @author Bas Leijdekkers
 */
public class EqualityToEqualsFixTest extends IGQuickFixesTestCase {

  public void testSimple() { doTest(EqualityToEqualsFix.getFixName(false)); }
  public void testPrecedence() { doTest(EqualityToEqualsFix.getFixName(false)); }
  public void testNegated() { doTest(EqualityToEqualsFix.getFixName(true)); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ObjectEqualityInspection());
    myFixture.enableInspections(new NumberEqualityInspection());
    myRelativePath = "bugs/equality_to_equals";
  }
}
