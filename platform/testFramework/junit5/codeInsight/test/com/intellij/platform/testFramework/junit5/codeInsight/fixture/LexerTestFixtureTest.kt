// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.lang.Language
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestFixtures
class LexerTestFixtureTest {
  private val fixture by lexerFixture("lexer/data") { WordLexer() }
  private val absoluteFixture by lexerFixture(IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/lexer/data") { WordLexer() }

  @Test
  fun testInlineExpected() {
    fixture.doTest("ab  cd", """
      WORD ('ab')
      SPACE ('  ')
      WORD ('cd')
    """.trimIndent())
  }

  @Test
  fun testMismatch() {
    assertThrows<AssertionError> {
      fixture.doTest("ab", "SPACE ('ab')")
    }
  }

  @Test
  fun testPathToTestDataFile() {
    assertEquals("pathToTestDataFile", fixture.testName)
    assertEquals(IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/lexer/data/pathToTestDataFile.txt", fixture.getPathToTestDataFile(".txt"))
  }

  @Test
  fun testAbsoluteDirPath() {
    assertEquals(fixture.getPathToTestDataFile(".txt"), absoluteFixture.getPathToTestDataFile(".txt"))
  }

  @Test
  fun `test name with spaces`() {
    assertEquals("name_with_spaces", fixture.testName)
  }

  @Test
  fun testZeroState() {
    fixture.checkZeroState("ab cd", TokenSet.create(WORD, SPACE))
    fixture.checkCorrectRestartUsingPosition("ab cd")
  }
}

private val WORD = IElementType("WORD", Language.ANY)
private val SPACE = IElementType("SPACE", Language.ANY)

private class WordLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var endOffset = 0
  private var tokenStart = 0
  private var tokenEnd = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.endOffset = endOffset
    tokenEnd = startOffset
    advance()
  }

  override fun advance() {
    tokenStart = tokenEnd
    if (tokenStart < endOffset) {
      val space = buffer[tokenStart] == ' '
      while (tokenEnd < endOffset && (buffer[tokenEnd] == ' ') == space) {
        tokenEnd++
      }
    }
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = when {
    tokenStart >= endOffset -> null
    buffer[tokenStart] == ' ' -> SPACE
    else -> WORD
  }

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = endOffset
}
