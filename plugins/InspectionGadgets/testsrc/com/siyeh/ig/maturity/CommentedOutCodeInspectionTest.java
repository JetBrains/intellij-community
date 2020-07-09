// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CommentedOutCodeInspectionTest extends LightJavaInspectionTestCase {

  public void testCommentedOutCode() {
    doTest();
    checkQuickFixAll();
  }

  public void testUncommentBlock() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("commented.out.code.uncomment.quickfix"));
  }

  public void testUncommentEndOfLine() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("commented.out.code.uncomment.quickfix"));
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    final CommentedOutCodeInspection inspection = new CommentedOutCodeInspection();
    inspection.minLines = 1;
    return inspection;
  }
}