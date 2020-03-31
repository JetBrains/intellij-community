// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.codeInsight

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.XCollection
import groovy.transform.CompileStatic
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/codeInsight")
@CompileStatic
class KtPluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myFixture.enableInspections(PluginXmlDomInspection.class)
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "codeInsight"
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String pathForClass = PathUtil.getJarPathForClass(XCollection.class)
    moduleBuilder.addLibrary("util", pathForClass)
    String langApiJar = PathUtil.getJarPathForClass(CompletionContributorEP.class)
    moduleBuilder.addLibrary("lang-api", langApiJar)
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.testHighlighting(true, false, false, filePaths)
  }

  void testLanguageAttributeHighlighting() {
    configureLanguageAttributeTest()
    doHighlightingTest("languageAttribute.xml", "MyLanguageAttributeEPBean.kt")
  }

  void testLanguageAttributeCompletion() {
    configureLanguageAttributeTest()
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.kt"))
    myFixture.configureByFile("languageAttribute.xml")


    def lookupElements = myFixture.complete(CompletionType.BASIC).sort { it.lookupString }
    assertEquals(2, lookupElements.length)
    assertLookupElement(lookupElements[0], "MyAnonymousLanguageID", "MyLanguage.MySubLanguage")
    assertLookupElement(lookupElements[1], "MyLanguageID", "MyLanguage")
  }

  private static void assertLookupElement(LookupElement element, String lookupString, String typeText) {
    def presentation = new LookupElementPresentation()
    element.renderElement(presentation)
    assertEquals(lookupString, presentation.itemText)
    assertEquals(typeText, presentation.typeText)
  }

  private void configureLanguageAttributeTest() {
    myFixture.addClass("package com.intellij.lang; " +
                       "public class Language { " +
                       "  protected Language(String id) {}" +
                       "}")
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.kt"))
  }
}
