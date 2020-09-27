// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.structuralsearch.PredefinedConfigurationUtil

class TemplatesCompletionTest : LightJavaCodeInsightFixtureTestCase() {

  fun prepare(text: String) {
    myFixture.configureByText(JavaFileType.INSTANCE, text)
    myFixture.editor.putUserData(StructuralSearchDialog.TEST_STRUCTURAL_SEARCH_DIALOG, true)
  }

  fun renderedLookupElementTexts(): Collection<String> {
    return myFixture.completeBasic()
      .map { LookupElementPresentation.renderElement(it) }
      .map { it.itemText + it.tailText }
  }

  fun testSameName() {
    prepare("all fields of<caret>")
    val customTemplate = PredefinedConfigurationUtil.createSearchTemplateInfo("all fields of a class", "",
                                                                              "", JavaFileType.INSTANCE)
    customTemplate.isPredefined = false
    ConfigurationManager.getInstance(project).addConfiguration(customTemplate)
    val elements = renderedLookupElementTexts()
    assert("all fields of a class (java search template)" in elements)
    assert("all fields of a class (java search template, user defined)" in elements)
  }

}