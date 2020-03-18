// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class OverridableMethodCallDuringObjectConstructionInspectionTest extends LightJavaInspectionTestCase {

  public void testOverridableMethodCallDuringObjectConstruction() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("make.method.final.fix.name", "a"));
  }

  public void testNoQuickFixes() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("make.class.final.fix.name", "NoQuickFixes"));
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("make.method.final.fix.name", "foo"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OverridableMethodCallDuringObjectConstructionInspection();
  }
}