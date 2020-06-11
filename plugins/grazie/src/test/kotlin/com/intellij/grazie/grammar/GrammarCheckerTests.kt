// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieTestBase
import org.junit.Assert
import org.junit.Test

class GrammarCheckerTests : GrazieTestBase() {

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
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"), text)
  }

  @Test
  fun `test few lines of text with typo on first line`() {
    val text = listOf("Tot he world, my dear friend!\n", "This is the start of a message.\n", "The end is also here world\n")
    val tokens = plain(text)
    val fixes = check(tokens)
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"), text[0])
  }

  @Test
  fun `test few lines of text with typo on last line`() {
    val text = listOf("Hello world!\n", "This is the start of a message.\n", "It is a the friend\n")
    val tokens = plain(text)
    val fixes = check(tokens)
    fixes.single().assertTypoIs(IntRange(6, 10), listOf("a", "the"), text[2])
  }

  @Test
  fun `test few lines of text with few typos`() {
    val text = listOf("Hello. World,, tot he.\n", "This are my friend.")
    val tokens = plain(text)
    val fixes = check(tokens).toList()
    Assert.assertEquals(3, fixes.size)
    fixes[0].assertTypoIs(IntRange(12, 13), listOf(","), text[0])
    fixes[1].assertTypoIs(IntRange(15, 20), listOf("to the"), text[0])
    fixes[2].assertTypoIs(IntRange(0, 3), listOf("These"), text[1])
  }

  @Test
  fun `test pretty formatted text with few typos`() {
    val text = listOf("English text.  Hello. World,, tot he.  \n  ", "     This is the next Javadoc string.   \n",
                      "    This are my friend.    ")
    val tokens = plain(text)
    val fixes = check(tokens).toList()
    Assert.assertEquals(3, fixes.size)
    fixes[0].assertTypoIs(IntRange(27, 28), listOf(","), text[0])
    fixes[1].assertTypoIs(IntRange(30, 35), listOf("to the"), text[0])
    fixes[2].assertTypoIs(IntRange(4, 7), listOf("These"), text[2])
  }
}
