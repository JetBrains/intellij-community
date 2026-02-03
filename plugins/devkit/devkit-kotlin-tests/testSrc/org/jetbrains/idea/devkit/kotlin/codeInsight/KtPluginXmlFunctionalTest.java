// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.codeInsight;

import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/pluginXml")
public class KtPluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PluginXmlDomInspection.class);
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "codeInsight/pluginXml";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(XCollection.class));
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(CompletionContributorEP.class));
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.testHighlighting(true, false, false, filePaths);
  }

  public void testLanguageAttributeHighlighting() {
    configureLanguageAttributeTest();
    doHighlightingTest("languageAttribute.xml", "MyLanguageAttributeEPBean.kt");
  }

  public void testLanguageAttributeCompletion() {
    configureLanguageAttributeTest();
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.kt"));
    myFixture.configureByFile("languageAttribute.xml");

    List<LookupElement> lookupElements = Arrays.stream(myFixture.complete(CompletionType.BASIC))
      .sorted(Comparator.comparing(item -> item.getLookupString()))
      .toList();
    assertEquals(2, lookupElements.size());
    assertLookupElement(lookupElements.get(0), "MyAnonymousLanguageID", "MyLanguage.MySubLanguage");
    assertLookupElement(lookupElements.get(1), "MyLanguageID", "MyLanguage");
  }

  private static void assertLookupElement(LookupElement element, String lookupString, String typeText) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    element.renderElement(presentation);
    assertEquals(lookupString, presentation.getItemText());
    assertEquals(typeText, presentation.getTypeText());
  }

  private void configureLanguageAttributeTest() {
    myFixture.addClass("package com.intellij.lang; " + "public class Language { " + "  protected Language(String id) {}" + "}");
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.kt"));
  }
}
