// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.lang.Language
import com.intellij.lexer.LexerBase
import com.intellij.lexer.RestartableLexer
import com.intellij.lexer.TokenIterator
import com.intellij.openapi.editor.ex.util.IntBasedStorage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestFixtures
class LexerTestFixtureTest {
  private val fixture by lexerFixture("lexer/data") { WordLexer() }
  private val absoluteFixture by lexerFixture(IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/lexer/data") { WordLexer() }
  private val negativeStateFixture by lexerFixture("lexer/data") { WordLexer(state = -1) }
  private val wideStateFixture by lexerFixture("lexer/data") { WordLexer(state = 0x8000) }
  private val customStorageFixture by lexerFixture("lexer/data", highlighterStorageFactory = { UnsignedStateStorage() }) {
    WordLexer(state = 0x8000)
  }
  private val noStorageFixture by lexerFixture("lexer/data", highlighterStorageFactory = null) { WordLexer(state = 0x10000) }

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
  fun testTrimRemovesControlChars() {
    fixture.doTest("ab", "WORD ('ab')\u0005")
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
  fun testStateInShortRange() {
    negativeStateFixture.doTest("ab cd", WORD_SPACE_WORD)
  }

  @Test
  fun testStateOutOfShortRange() {
    val error = assertThrows<AssertionError> {
      wideStateFixture.doTest("ab cd", WORD_SPACE_WORD)
    }
    assertTrue(error.message!!.startsWith("The state 0x00008000 (32768) at offset 0 changes to 0xFFFF8000 (-32768)")) { error.message }
    assertTrue(error.message!!.contains("highlighterStorageFactory")) { error.message }
  }

  @Test
  fun testStateInCustomStorage() {
    customStorageFixture.doTest("ab cd", WORD_SPACE_WORD)
  }

  @Test
  fun testNoStorage() {
    noStorageFixture.doTest("ab cd", WORD_SPACE_WORD)
  }

  @Test
  fun testZeroState() {
    fixture.checkZeroState("ab cd", TokenSet.create(WORD, SPACE))
    fixture.checkCorrectRestartUsingPosition("ab cd")
  }
}

/** The test application makes a logged error fail the test, as it does in a lexer test of a plugin. */
@TestApplication
class LexerTestFixtureLoggedErrorTest {
  private val unregisteredTypeFixture by lexerFixture("lexer/data") { WordLexer(state = -1, wordType = UNREGISTERED_WORD) }

  @Test
  fun testUnregisteredTokenType() {
    unregisteredTypeFixture.doTest("ab cd", WORD_SPACE_WORD)
  }
}

private val WORD = IElementType("WORD", Language.ANY)
private val SPACE = IElementType("SPACE", Language.ANY)

/** A token type of a parsing lexer that the editor highlighter never sees, such as an Angular block parameter. */
private val UNREGISTERED_WORD = object : IElementType("WORD", Language.ANY, false) {}
private const val WORD_SPACE_WORD = "WORD ('ab')\nSPACE (' ')\nWORD ('cd')"

/** Keeps the state in the 16 high bits without the sign, as `TextMateLexerDataStorage` does. */
private class UnsignedStateStorage : IntBasedStorage() {
  override fun unpackStateFromData(data: Int): Int = data ushr 16
}

/**
 * Gives [state] to each token and [wordType] to each word. When [state] is not 0, the lexer is a [RestartableLexer] with no restartable state,
 * so the restart check skips each token and only the check of the highlighter storage runs.
 */
private class WordLexer(private val state: Int = 0, private val wordType: IElementType = WORD) : LexerBase(), RestartableLexer {
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

  override fun getState(): Int = state

  override fun getStartState(): Int = 0

  override fun isRestartableState(state: Int): Boolean = state == 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator) {
    start(buffer, startOffset, endOffset, initialState)
  }

  override fun getTokenType(): IElementType? = when {
    tokenStart >= endOffset -> null
    buffer[tokenStart] == ' ' -> SPACE
    else -> wordType
  }

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = endOffset
}
