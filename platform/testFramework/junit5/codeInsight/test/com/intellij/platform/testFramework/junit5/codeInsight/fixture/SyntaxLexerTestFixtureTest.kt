// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.editor.ex.util.IntBasedStorage
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.RestartableLexer
import com.intellij.platform.syntax.lexer.TokenIterator
import com.intellij.platform.syntax.util.lexer.LexerBase
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestFixtures
class SyntaxLexerTestFixtureTest {
  private val fixture by syntaxLexerFixture("lexer/data") { SyntaxWordLexer(restartable = true) }
  private val absoluteFixture by syntaxLexerFixture(IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/lexer/data") { SyntaxWordLexer(restartable = true) }
  private val brokenRestartFixture by syntaxLexerFixture("lexer/data") { SyntaxWordLexer(restartable = false) }
  private val noRestartCheckFixture by syntaxLexerFixture("lexer/data", checkRestart = false) { SyntaxWordLexer(restartable = false) }
  private val wideStateFixture by syntaxLexerFixture("lexer/data") { SyntaxWordLexer(restartable = true, state = 0x8000) }
  private val customStorageFixture by syntaxLexerFixture("lexer/data", highlighterStorageFactory = { SyntaxUnsignedStateStorage() }) {
    SyntaxWordLexer(restartable = true, state = 0x8000)
  }
  private val noStorageFixture by syntaxLexerFixture("lexer/data", highlighterStorageFactory = null) {
    SyntaxWordLexer(restartable = true, state = 0x10000)
  }

  @Test
  fun testInlineExpected() {
    fixture.doTest("ab  cd\nef", """
      WORD ('ab')
      SPACE ('  ')
      WORD ('cd\nef')
    """.trimIndent())
  }

  @Test
  fun testMismatch() {
    assertThrows<AssertionError> {
      fixture.doTest("ab", "SPACE ('ab')")
    }
  }

  @Test
  fun testExplicitLexer() {
    fixture.doTest("ab cd", "WORD ('ab')\nSPACE (' ')\nWORD ('cd')", SyntaxWordLexer(restartable = true))
  }

  @Test
  fun testBrokenRestart() {
    val expected = "WORD ('ab')\nSPACE (' ')\nWORD ('cd')"
    assertThrows<AssertionError> {
      brokenRestartFixture.doTest("ab cd", expected)
    }
    noRestartCheckFixture.doTest("ab cd", expected)
  }

  @Test
  fun testStateOutOfShortRange() {
    val error = assertThrows<AssertionError> {
      wideStateFixture.doTest("ab cd", "WORD ('ab')\nSPACE (' ')\nWORD ('cd')")
    }
    assertTrue(error.message!!.startsWith("The state 0x00008000 (32768) at offset 0 changes to 0xFFFF8000 (-32768)")) { error.message }
    assertTrue(error.message!!.contains("highlighterStorageFactory")) { error.message }
  }

  @Test
  fun testStateInCustomStorage() {
    customStorageFixture.doTest("ab cd", "WORD ('ab')\nSPACE (' ')\nWORD ('cd')")
  }

  @Test
  fun testNoStorage() {
    noStorageFixture.doTest("ab cd", "WORD ('ab')\nSPACE (' ')\nWORD ('cd')")
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
}

private val SYNTAX_WORD = SyntaxElementType("WORD")
private val SYNTAX_SPACE = SyntaxElementType("SPACE")

/** Keeps the state in the 16 high bits without the sign, as `TextMateLexerDataStorage` does. */
private class SyntaxUnsignedStateStorage : IntBasedStorage() {
  override fun unpackStateFromData(data: Int): Int = data ushr 16
}

/**
 * When [restartable] is `false`, the lexer ignores the start offset and always lexes from the start of the buffer.
 * The lexer gives [state] to each token. A state that is not 0 is not restartable,
 * so the restart check skips each token and only the check of the highlighter storage runs.
 */
private class SyntaxWordLexer(private val restartable: Boolean, private val state: Int = 0) : LexerBase(), RestartableLexer {
  private var buffer: CharSequence = ""
  private var endOffset = 0
  private var tokenStart = 0
  private var tokenEnd = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.endOffset = endOffset
    tokenEnd = if (restartable) startOffset else 0
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

  override fun getState(): Int = state

  override fun getStartState(): Int = 0

  override fun isRestartableState(state: Int): Boolean = state == 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator) {
    start(buffer, startOffset, endOffset, initialState)
  }

  override fun getTokenType(): SyntaxElementType? = when {
    tokenStart >= endOffset -> null
    buffer[tokenStart] == ' ' -> SYNTAX_SPACE
    else -> SYNTAX_WORD
  }

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = endOffset
}
