// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.intention.IntentionAction;
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
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("make.method.final.fix.name", "a"));
    assertNotNull(intention);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  public void testIntentionPreviewWhenMethodIsInAnotherFile() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.addClass("""
                         class One { 
                           public void a() {}
                         }
                         """);
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("make.method.final.fix.name", "a"));
    assertNotNull(intention);
    String customPreviewText = myFixture.getIntentionPreviewText(intention);
    assertEquals("public final void a() {}", customPreviewText);
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