// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.List;

public class DuplicateStringLiteralInspectionTest extends JavaCodeInsightFixtureTestCase {
  private DuplicateStringLiteralInspection myInspection = new DuplicateStringLiteralInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(myInspection);
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  public void testPropertyKey() {
    myInspection.IGNORE_PROPERTY_KEYS = true;
    try {
      myFixture.testHighlighting("PropertyKey.java");
    } finally {
      myInspection.IGNORE_PROPERTY_KEYS = false;
    }
  }

  public void testBatchApplication() {
    final List<IntentionAction> fixes = myFixture.getAllQuickFixes("ApplyRenameForWholeFile.java");
    fixes
      .stream()
      .filter(i -> JavaI18nBundle.message("inspection.duplicates.replace.family.quickfix").equals(i.getFamilyName()))
      .forEach(action -> {
        myFixture.launchAction(action);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      });
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
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(false) + "After.java");
  }
}