/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.List;

public class DuplicateStringLiteralInspectionTest extends JavaCodeInsightFixtureTestCase {
  private DuplicateStringLiteralInspection myInspection = new DuplicateStringLiteralInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(myInspection);
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  public void testPropertyKey() throws Exception {
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