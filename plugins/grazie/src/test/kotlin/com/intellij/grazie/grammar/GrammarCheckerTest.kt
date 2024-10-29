// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import org.junit.Assert
import org.junit.Test

class GrammarCheckerTest : GrazieTestBase() {

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
    Assert.assertEquals(4, fixes.size)
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
    Assert.assertEquals(4, fixes.size)
    fixes[0].assertTypoIs(IntRange(27, 28), listOf(","))
    fixes[1].assertTypoIs(IntRange(30, 35), listOf("to the"))
    fixes[2].assertTypoIs(IntRange(0, 3), listOf("These"))
    fixes[3].assertTypoIs(IntRange(5, 7), listOf("is"))
  }

  @Test
  fun `test German text`() {
    val text = listOf("Es ist jetzt 15:30 Uhr.")
    assertEmpty(check(plain(text)))

    GrazieConfig.update { it.copy(enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.SWISS_GERMAN)) }
    psiManager.dropPsiCaches()
    assertOneElement(check(plain(text))).assertTypoIs(IntRange(15, 15), listOf("."))
  }
}
