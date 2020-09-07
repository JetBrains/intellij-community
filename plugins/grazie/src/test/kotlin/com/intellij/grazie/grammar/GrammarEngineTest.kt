// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieTestBase
import org.junit.Test

class GrammarEngineTest : GrazieTestBase() {

  @Test
  fun `test empty text`() {
    val fixes = GrammarEngine.getTypos("")
    assertIsEmpty(fixes)
  }

  @Test
  fun `test correct text`() {
    val fixes = GrammarEngine.getTypos("Hello world")
    assertIsEmpty(fixes)
  }

  @Test
  fun `test correct few lines text`() {
    val text = """
            |Hello world!
            |This is the start of a message.
            |The end is also here.
        """.trimMargin()
    val fixes = GrammarEngine.getTypos(text)
    assertIsEmpty(fixes)
  }


  @Test
  fun `test one line text with typo`() {
    val text = "Tot he world, my dear friend"
    val fixes = GrammarEngine.getTypos(text).toList()
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"), text)
  }

  @Test
  fun `test few lines text with typo on first line`() {
    val text = """
            |Tot he world!
            |This is the start of a message.
            |The end is also here world.
        """.trimMargin()
    val fixes = GrammarEngine.getTypos(text)
    fixes.single().assertTypoIs(IntRange(0, 5), listOf("To the"), text)
  }

  @Test
  fun `test few lines text with typo on last line`() {
    val text = """
            |Hello world!
            |This is the start of a message.
            |It is a the friend.
        """.trimMargin()
    val fixes = GrammarEngine.getTypos(text)
    fixes.single().assertTypoIs(IntRange(51, 55), listOf("a", "the"), text)
  }

  @Test
  fun `test few lines text with few typos`() {
    val text = """
            |Hello. world,, tot he.
            |This are my friend.""".trimMargin()
    val fixes = GrammarEngine.getTypos(text).toList()
    assertEquals(3, fixes.size)
    fixes[0].assertTypoIs(IntRange(12, 13), listOf(","), text)
    fixes[1].assertTypoIs(IntRange(15, 20), listOf("to the"), text)
    fixes[2].assertTypoIs(IntRange(23, 26), listOf("These"), text)
  }
}
