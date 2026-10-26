// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.filterFor
import com.intellij.grazie.utils.toProofreadingContext
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class GrammarCheckerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture, additionalEnabledRules = setOf("LanguageTool.DE.ZEITANGABEN"))

  @Test
  fun `test empty text`() {
    val token = plain("")
    val fixes = check(token)
    assertIsEmpty(fixes)
  }

  @Test
  fun `test correct text`() {
    val token = plain("Hello world")
    val fixes = check(token)
    assertIsEmpty(fixes)
  }

  @Test
  fun `test few lines of correct text`() {
    val tokens = plain("Hello world!\n", "This is the start of a message.\n", "The end is also here.")
    val fixes = check(tokens)
    assertIsEmpty(fixes)
  }


  @Test
  fun `test one line of text with one typo`() {
    val text = "Tot he world, my dear friend"
    val tokens = plain(text).toList()
    val fixes = check(tokens)
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"))
  }

  @Test
  fun `test few lines of text with typo on first line`() {
    val text = listOf("Tot he world, my dear friend!\n", "This is the start of a message.\n", "The end is also here world\n")
    val tokens = plain(text)
    val fixes = check(tokens)
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"))
  }

  @Test
  fun `test few lines of text with typo on last line`() {
    val text = listOf("Hello world!\n", "This is the start of a message.\n", "It is a the friend\n")
    val tokens = plain(text)
    val fixes = check(tokens)
    fixes.single().assertTypoIs(IntRange(6, 10), listOf("a", "the"))
  }

  @Test
  fun `test few lines of text with few typos`() {
    val text = listOf("Hello. World,, tot he.\n", "This are my friend.")
    val tokens = plain(text)
    val fixes = check(tokens).toList()
    assertEquals(4, fixes.size)
    fixes[0].assertTypoIs(IntRange(12, 13), listOf(","))
    fixes[1].assertTypoIs(IntRange(15, 20), listOf("to the"))
    fixes[2].assertTypoIs(IntRange(0, 3), listOf("These"))
    fixes[3].assertTypoIs(IntRange(5, 7), listOf("is"))
  }

  @Test
  fun `test pretty formatted text with few typos`() {
    val text = listOf("English text.  Hello. World,, tot he.  \n  ", "     This is the next Javadoc string.   \n",
                      "    This are my friend.    ")
    val tokens = plain(text)
    val fixes = check(tokens).toList()
    assertEquals(4, fixes.size)
    fixes[0].assertTypoIs(IntRange(27, 28), listOf(","))
    fixes[1].assertTypoIs(IntRange(30, 35), listOf("to the"))
    fixes[2].assertTypoIs(IntRange(0, 3), listOf("These"))
    fixes[3].assertTypoIs(IntRange(5, 7), listOf("is"))
  }

  @Test
  fun `test German text`() {
    val text = listOf("Es ist jetzt 15:30 Uhr.")
    assertIsEmpty(check(plain(text)))

    GrazieConfig.update { it.copy(enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.SWISS_GERMAN)) }
    grazie.dropPsiCaches()
    val problems = check(plain(text))
    assertEquals(1, problems.size, problems.toString())
    problems.single().assertTypoIs(IntRange(15, 15), listOf("."))
  }

  @Test
  fun `test Russian text`() {
    GrazieConfig.update { it.copy(enabledLanguages = setOf(Lang.RUSSIAN)) }
    assertIsEmpty(check(plain("Времено отключен, т.к. не работает.")))
  }

  @Test
  fun `test text with identifiers and call expression`() {
    val token = plain("Using auto_cleanup is generally more eficient, in which case you shouldn't ever need to cleanup()")
    assertIsEmpty(check(token))
  }

  private fun plain(vararg texts: String) = plain(texts.toList())

  private fun plain(texts: List<String>): Collection<PsiElement> {
    return texts.flatMap {
      val file = grazie.fixture.configureByText("${it.hashCode()}.txt", it)
      runReadActionBlocking { file.filterFor<PsiPlainText>() }
    }
  }

  private fun check(tokens: Collection<PsiElement>): List<TextProblem> {
    return runReadActionBlocking {
      tokens.flatMap {
        TextExtractor.findTextsAt(it, TextContent.TextDomain.ALL).flatMap { text ->
          LanguageToolChecker().check(text.toProofreadingContext())
        }
      }
    }
  }
}
