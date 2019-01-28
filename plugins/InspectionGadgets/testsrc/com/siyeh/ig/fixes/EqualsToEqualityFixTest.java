// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.EqualsCalledOnEnumConstantInspection;

/**
 * @author Bas Leijdekkers
 */
public class EqualsToEqualityFixTest extends IGQuickFixesTestCase {

  public void testSwitchExpression() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new EqualsCalledOnEnumConstantInspection());
    myRelativePath = "fixes/equals_to_equality";
    myDefaultHint = InspectionGadgetsBundle.message("equals.to.equality.quickfix");
  }
}
