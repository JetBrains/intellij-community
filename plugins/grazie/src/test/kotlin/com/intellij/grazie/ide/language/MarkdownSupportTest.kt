// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("NonAsciiCharacters")

package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.spellchecker.dictionary.Dictionary


class MarkdownSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf(
    "LanguageTool.EN.COMMA_COMPOUND_SENTENCE",
    "LanguageTool.EN.EN_QUOTES"
  )

  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/markdown/Example.md")
  }

  fun `test grazie spellchecker in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    runHighlightTestForFile("ide/language/markdown/Spellcheck.md")
  }

  fun `test replacement with markup inside`() {
    myFixture.configureByText("a.md", "Please, <STYLE_SUGGESTION descr=\"GATHER_UP\">gather<caret> </STYLE_SUGGESTION>[<STYLE_SUGGESTION descr=\"GATHER_UP\">up</STYLE_SUGGESTION> the](url) documentation.")
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("gather"))
    myFixture.checkResult("Please, gather[ the](url) documentation.") // the result could be different, but the markup should still be preserved
  }

  fun `test no highlighting in a very large file to avoid slow analysis`() {
    val text = "This is an very nice mistake in English text.\n\n".repeat(10_000)
    myFixture.configureByText("a.md", text)
    myFixture.checkHighlighting()
  }

  fun `test no style warning highlighting for picky passive voice rules`() {
    myFixture.configureByText("a.md", """
      Many objects are disposed automatically by the platform if they implement the Disposable interface. 
      The most important type of such objects is services. Application-level services are automatically disposed 
      by the platform when the IDE is closed or the plugin providing the service is unloaded.
      Project-level services are disposed on project close or plugin upload events.
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test grazie rule controls associated LT rules`() {
    // ALL_OF_THE is enabled by default in LT, so the style error should be detected
    GrazieConfig.update { it.copy(userEnabledRules = setOf(), userDisabledRules = setOf()) }
    myFixture.configureByText("a.md", """
      <STYLE_SUGGESTION descr="ALL_OF_THE">All of the</STYLE_SUGGESTION> people I know came.
     """.trimIndent())
    myFixture.checkHighlighting()

    // Grazie REDUNDANT_OF rule controls associated ALL_OF_THE LT rule, hence no style error should be detected
    GrazieConfig.update { it.copy(userEnabledRules = setOf(), userDisabledRules = setOf("Grazie.RuleEngine.En.Style.REDUNDANT_OF")) }
    myFixture.configureByText("a.md", "All of the people I know came.")
    myFixture.checkHighlighting()

    // Explicitly enabled ALL_OF_THE rule should still detect the style error
    GrazieConfig.update {
      it.copy(
        userEnabledRules = setOf("LanguageTool.EN.ALL_OF_THE"),
        userDisabledRules = setOf("Grazie.RuleEngine.En.Style.REDUNDANT_OF")
      )
    }
    myFixture.configureByText("a.md", """
      <STYLE_SUGGESTION descr="ALL_OF_THE">All of the</STYLE_SUGGESTION> people I know came.
     """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test no typos in chinese texts with text-level spellchecker`() {
    // It may look like a synthetic example that has nothing to do with the real world,
    // but this is exactly what [androidDictionary] looks like
    val name = "androidDictionary"
    val words = setOf("foldable", "rollable", "wearable")
    getInstance(project).spellChecker!!.addDictionary(object : Dictionary {
      override fun getName(): String = name
      override fun contains(word: String): Boolean = word in words
      override fun getWords(): Set<String> = words
    })
    Disposer.register(testRootDisposable) { getInstance(project).spellChecker!!.removeDictionary(name) }

    enableProofreadingFor(setOf(Lang.CHINESE))
    myFixture.configureByText("a.md", """
      文件不存在时应返回错误
      相对路径不存在
      期望返回错误但未返回
      不应返回错误

      <!-- 将文本粘贴在此，或者检测以下文本 我和她去看了<GRAMMAR_ERROR descr="wa5">二部</GRAMMAR_ERROR>电影。-->
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}
