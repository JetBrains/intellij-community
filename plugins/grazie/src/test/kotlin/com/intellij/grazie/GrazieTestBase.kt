// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.filterFor
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

abstract class GrazieTestBase : BasePlatformTestCase() {
  companion object {
    val inspectionTools: Array<out LocalInspectionTool> by lazy { arrayOf(GrazieInspection(), SpellCheckingInspection()) }
    val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN, Lang.RUSSIAN, Lang.ITALIAN)
    val enabledRules = setOf("LanguageTool.EN.COMMA_WHICH", "LanguageTool.EN.UPPERCASE_SENTENCE_START")
  }

  protected open val additionalEnabledRules: Set<String> = emptySet()

  protected open val additionalEnabledContextLanguages: Set<Language> = emptySet()

  override fun getBasePath() = "community/plugins/grazie/src/test/testData"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(*inspectionTools)

    GrazieConfig.update { state ->
      val checkingContext = state.checkingContext.copy(
        isCheckInStringLiteralsEnabled = true,
        isCheckInCommentsEnabled = true,
        isCheckInDocumentationEnabled = true,
        enabledLanguages = additionalEnabledContextLanguages.map { it.id }.toSet(),
      )
      state.copy(
        enabledLanguages = enabledLanguages,
        userEnabledRules = enabledRules + additionalEnabledRules,
        checkingContext = checkingContext
      )
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val newExtensions = TextChecker.allCheckers().map { if (it is LanguageToolChecker) LanguageToolChecker.TestChecker() else it }
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), newExtensions, testRootDisposable)
  }

  override fun tearDown() {
    try {
      GrazieConfig.update { GrazieConfig.State() }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
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

  fun check(tokens: Collection<PsiElement>): List<TextProblem> {
    return tokens.flatMap {
      TextExtractor.findTextsAt(it, TextContent.TextDomain.ALL).flatMap { text ->
        runBlocking {
          blockingContext {
            LanguageToolChecker().check(text)
          }
        }
      }
    }
  }
}
