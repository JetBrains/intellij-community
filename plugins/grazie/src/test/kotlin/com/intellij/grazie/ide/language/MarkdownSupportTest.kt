// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("NonAsciiCharacters")

package com.intellij.grazie.ide.language

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Absent
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test


@TestApplication
class MarkdownSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture, additionalEnabledRules = setOf(
    "LanguageTool.EN.COMMA_COMPOUND_SENTENCE",
    "LanguageTool.EN.EN_QUOTES",
    "LanguageTool.EN.DASH_RULE"
  ))

  @Test
  fun `test grammar check in file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/markdown/Example.md")
  }

  @Test
  fun `test grazie spellchecker in file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    grazie.runHighlightTestForFile("ide/language/markdown/Spellcheck.md")
  }

  @Test
  fun `test no false positives in test links`() {
    grazie.runHighlightTestForFile("ide/language/markdown/TestLink.md")
  }

  @Test
  fun `test replacement with markup inside`() {
    grazie.fixture.configureByText("a.md", "Please, <STYLE_SUGGESTION descr=\"GATHER_UP\">gather<caret> </STYLE_SUGGESTION>[<STYLE_SUGGESTION descr=\"GATHER_UP\">up</STYLE_SUGGESTION> the](url) documentation.")
    grazie.fixture.checkHighlighting()
    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("gather"))
    grazie.fixture.checkResult("Please, gather[ the](url) documentation.") // the result could be different, but the markup should still be preserved
  }

  @Test
  fun `test no highlighting in a very large file to avoid slow analysis`() {
    val text = "This is an very nice mistake in English text.\n\n".repeat(10_000)
    grazie.fixture.configureByText("a.md", text)
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no style warning highlighting for picky passive voice rules`() {
    grazie.fixture.configureByText("a.md", """
      Many objects are disposed automatically by the platform if they implement the Disposable interface. 
      The most important type of such objects is services. Application-level services are automatically disposed 
      by the platform when the IDE is closed or the plugin providing the service is unloaded.
      Project-level services are disposed on project close or plugin upload events.
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test html entity excluded before grazie checks`() {
    grazie.fixture.configureByText("a.md", "You&#39;re here.")
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no LT article warning before number starting with a vowel`() {
    grazie.fixture.configureByText("a.md", "Extract an 8x8 pixel square from an 11x11 square from an 18432x18432 square from an 11000.")
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no em dash warning for list bullet in injected markdown`() {
    grazie.fixture.configureByText("a.md", """
      Something
      ```markdown
      ## Goals
      - Primary outcomes the feature must deliver.
      ```
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no typos in asian-english mixed texts with text-level spellchecker`() {
    // It may look like a synthetic example that has nothing to do with the real world,
    // but this is exactly what [androidDictionary] looks like
    val mixedText = """
      文件不存在时应返回错误
      相对路径不存在
      期望返回错误但未返回
      不应返回错误
      Hello不应返回错误
      不应返回错误hello
      不应HELLO返回错误
      "文件'Love'文件", "Love'文件", "文件'Love" ,"文件\"Love\"文件", "文件`Love`文件", "文件Love", "Love文件",
      
      // Some Japanese too
      次のprojectのdeadlineは来週の金曜日なので、急いでfinishしなければなりません。
    """.trimIndent()
    grazie.fixture.configureByText("a.md", mixedText)
    grazie.fixture.checkHighlighting()

    val project = grazie.project
    val name = "androidDictionary"
    val words = setOf("foldable", "rollable", "wearable")
    getInstance(project).spellChecker!!.addDictionary(object : Dictionary {
      override fun getName(): String = name
      override fun lookup(word: String): LookupStatus = if (word in words) Present else Absent
      override fun getWords(): Set<String> = words
    })
    Disposer.register(grazie.testRootDisposable) { getInstance(project).spellChecker!!.removeDictionary(name) }
    grazie.fixture.configureByText("a.md", mixedText)
    grazie.fixture.checkHighlighting()

    grazie.enableProofreadingFor(setOf(Lang.CHINESE, Lang.JAPANESE))
    grazie.fixture.configureByText("a.md", mixedText + """

      <!-- 将文本粘贴在此，或者检测以下文本 我和她去看了<GRAMMAR_ERROR descr="wa5">二部</GRAMMAR_ERROR>电影。-->
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test grammar check in html comment`() {
    grazie.fixture.configureByText("a.md", """
      <!-- It is <GRAMMAR_ERROR <TYPO descr="Typo: In word 'descr'">descr</TYPO>=\"EN_A_VS_AN\">an</GRAMMAR_ERROR> friend there -->
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }
}
