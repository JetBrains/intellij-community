/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.List;

public class DuplicateStringLiteralInspectionTest extends JavaCodeInsightFixtureTestCase {
  private final DuplicateStringLiteralInspection myInspection = new DuplicateStringLiteralInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(myInspection);
  }

  public void testPropertyKey() throws Exception {
    myInspection.IGNORE_PROPERTY_KEYS = true;
    myFixture.testHighlighting("PropertyKey.java");
  }

  public void testBatchApplication() {
    final List<IntentionAction> fixes = myFixture.getAllQuickFixes("ApplyRenameForWholeFile.java");
    fixes
      .stream()
      .filter(i -> InspectionsBundle.message("inspection.duplicates.replace.family.quickfix").equals(i.getFamilyName()))
      .forEach(myFixture::launchAction);
    myFixture.checkResultByFile("ApplyRenameForWholeFileAfter.java");
  }

  public void testInvalidForwardReference() {
    doTestFix();
  }

  public void testRemoveRedundantQualifier() {
    doTestFix();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections/duplicateStringLiteral/";
  }

  private void doTestFix() {
    myFixture.configureByFile(getTestName(false) + ".java");
    final IntentionAction fix = myFixture.findSingleIntention("Replace");
    assertNotNull(fix);
    myFixture.launchAction(fix);
    myFixture.checkResultByFile(getTestName(false) + "After.java");
  }
}