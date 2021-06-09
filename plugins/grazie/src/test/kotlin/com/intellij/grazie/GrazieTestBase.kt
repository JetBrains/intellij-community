// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.filterFor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class GrazieTestBase : BasePlatformTestCase() {
  companion object {
    val inspectionTools by lazy { arrayOf(GrazieInspection(), SpellCheckingInspection()) }
    val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN, Lang.RUSSIAN)
    val enabledRules = setOf("LanguageTool.EN.COMMA_WHICH")
  }

  protected open val additionalEnabledRules: Set<String> = emptySet()

  override fun getBasePath() = "community/plugins/grazie/src/test/testData"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(*inspectionTools)

    if (GrazieConfig.get().enabledLanguages != enabledLanguages) {
      GrazieConfig.update { state ->
        val checkingContext = state.checkingContext.copy(
          isCheckInStringLiteralsEnabled = true,
          isCheckInCommentsEnabled = true,
          isCheckInDocumentationEnabled = true
        )
        state.copy(
          enabledLanguages = enabledLanguages,
          userEnabledRules = enabledRules + additionalEnabledRules,
          checkingContext = checkingContext
        )
      }

      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  protected open fun runHighlightTestForFile(file: String) {
    myFixture.configureByFile(file)
    myFixture.checkHighlighting(true, false, false)
  }

  fun plain(vararg texts: String) = plain(texts.toList())

  fun plain(texts: List<String>): Collection<PsiElement> {
    return texts.flatMap { myFixture.configureByText("${it.hashCode()}.txt", it).filterFor<PsiPlainText>() }
  }

  fun check(tokens: Collection<PsiElement>): List<LanguageToolChecker.Problem> {
    return tokens.flatMap {
      val text = TextExtractor.findTextAt(it, TextContent.TextDomain.ALL)
      if (text == null) emptyList() else LanguageToolChecker.checkText(text)
    }
  }
}
