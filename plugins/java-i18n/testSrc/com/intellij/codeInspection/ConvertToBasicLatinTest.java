// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.i18n.ConvertToBasicLatinInspection;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ConvertToBasicLatinTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/java-i18n/testData/quickFix/convertToBasicLatin";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ConvertToBasicLatinInspection());
  }

  public void testCharLiteral() { doTest(); }
  public void testStringLiteral() { doTest(); }
  public void testPlainComment() { doTest(); }
  public void testDocComment() { doTest(); }
  public void testDocTag() { doTest(); }
  public void testUnclosedStringLiteral() { doTest(); }
  public void testUnclosedPlainComment() { doTest(); }
  public void testUnclosedDocComment() { doTest(); }

  private void doTest() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(JavaI18nBundle.message("inspection.non.basic.latin.character.quickfix"));
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }
}