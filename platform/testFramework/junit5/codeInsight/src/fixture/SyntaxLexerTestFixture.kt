// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.editor.ex.util.DataStorage
import com.intellij.openapi.editor.ex.util.IntBasedStorage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.RestartableLexer
import com.intellij.psi.TokenType
import com.intellij.testFramework.HighlighterStorageCheck
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import java.util.function.Supplier

/**
 * Lexer test helpers for a JUnit 5 test of a lexer of the syntax library. Create the fixture with [syntaxLexerFixture].
 * For a [com.intellij.lexer.Lexer], use [lexerFixture].
 *
 * The fixture needs no test application.
 */
@TestOnly
class SyntaxLexerTestFixture internal constructor(
  private val lexerFactory: Supplier<out Lexer>,
  private val dirPath: String,
  /**
   * The name of the current test method without the `test` prefix and with a lowercase first letter.
   * The name has no leading or trailing spaces. Each other space in the name becomes an underscore.
   */
  val testName: String,
  private val expectedFileExtension: String,
  private val checkRestart: Boolean,
  private val highlighterStorageFactory: Supplier<out DataStorage>?,
) {
  fun createLexer(): Lexer = lexerFactory.get()

  /**
   * Tokenizes [text] with [lexer] and compares the tokens with [expected].
   * When [expected] is `null`, compares the tokens with the file of [getPathToTestDataFile] for the expected file extension.
   * Then calls [checkCorrectRestart], if the fixture checks the restart. The check uses new lexers, not [lexer].
   */
  @JvmOverloads
  fun doTest(text: String, expected: String? = null, lexer: Lexer = createLexer()) {
    val result = printTokens(text, 0, lexer)
    if (expected != null) {
      assertSameLexerTokens(expected, result)
    }
    else {
      PlatformTestUtil.assertSameLinesWithFile(getPathToTestDataFile(expectedFileExtension), result)
    }
    if (checkRestart) {
      checkCorrectRestart(text)
    }
  }

  /**
   * Loads the file of [getPathToTestDataFile] for [fileExt] and calls [doTest] with its text.
   * [fileExt] has no leading dot.
   */
  fun doFileTest(fileExt: String) {
    doTest(loadTestDataFile(".$fileExt"))
  }

  fun printTokens(text: CharSequence, start: Int): String = printTokens(text, start, createLexer())

  /**
   * Returns the path of the test data file for the current test. [extension] starts with a dot.
   */
  fun getPathToTestDataFile(extension: String): String = lexerTestDataPath(dirPath, testName, extension)

  /**
   * Returns the trimmed text of the file of [getPathToTestDataFile] for [fileExt], with normalized line separators.
   * [fileExt] starts with a dot.
   */
  fun loadTestDataFile(fileExt: String): String = loadLexerTestData(getPathToTestDataFile(fileExt), trim = true)

  /**
   * Verifies that the lexer produces the same token sequence when restarted from any position
   * where [Lexer.getState] returns zero or [RestartableLexer.isRestartableState] returns `true`.
   * For a [RestartableLexer], it also checks that each state is the same after a round trip through the highlighter storage.
   * The fixture cannot convert a [SyntaxElementType] to an `IElementType`, so the check packs each state with [TokenType.WHITE_SPACE].
   *
   * For every such position the lexer is restarted via [Lexer.start] with the recorded offset and state,
   * and the resulting tokens are compared against the tail of the initial full-text tokenization.
   */
  fun checkCorrectRestart(text: String) {
    val mainLexer = createLexer()
    val allTokens = tokenize(text, 0, 0, mainLexer)
    val auxLexer = createLexer()
    val storage = if (auxLexer is RestartableLexer) highlighterStorageFactory?.get() else null
    auxLexer.start(text)
    var index = 0
    while (auxLexer.getTokenType() != null) {
      val state = auxLexer.getState()
      if (storage != null) {
        val isRestartable = (auxLexer as RestartableLexer).isRestartableState(state)
        val storedState = storage.unpackStateFromData(storage.packData(TokenType.WHITE_SPACE, state, isRestartable))
        if (state != storedState) {
          fail(HighlighterStorageCheck.mismatchMessage(auxLexer.getTokenStart(), state, storedState, storage, PASS_STORAGE_HINT))
        }
      }
      if (state == 0 || (auxLexer is RestartableLexer && auxLexer.isRestartableState(state))) {
        val tokenStart = auxLexer.getTokenStart()
        val expectedTokens = allTokens.subList(index, allTokens.size)
        val restartedTokens = tokenize(text, tokenStart, state, mainLexer)
        assertEquals(expectedTokens.joinToString("\n"), restartedTokens.joinToString("\n")) {
          "Restarting impossible from offset $tokenStart `${auxLexer.getTokenText()}`\n" +
          "All tokens <type, offset, lexer state>: $allTokens\n"
        }
      }
      index++
      auxLexer.advance()
    }
  }

  private data class TokenState(val type: SyntaxElementType, val offset: Int, val state: Int)

  companion object {
    @JvmStatic
    fun printTokens(text: CharSequence, start: Int, lexer: Lexer): String {
      lexer.start(text, start, text.length)
      val result = StringBuilder()
      while (true) {
        val tokenType = lexer.getTokenType() ?: break
        result.append(printSingleToken(text, tokenType, lexer.getTokenStart(), lexer.getTokenEnd()))
        lexer.advance()
      }
      return result.toString()
    }

    @JvmStatic
    fun printSingleToken(fileText: CharSequence, tokenType: SyntaxElementType, start: Int, end: Int): String {
      return "$tokenType ('${StringUtil.replace(fileText.subSequence(start, end).toString(), "\n", "\\n")}')\n"
    }

    private fun tokenize(text: String, start: Int, state: Int, lexer: Lexer): List<TokenState> {
      try {
        lexer.start(text, start, text.length, state)
      }
      catch (t: Throwable) {
        throw IllegalStateException("Restarting impossible from offset $start", t)
      }
      val result = ArrayList<TokenState>()
      while (true) {
        val tokenType = lexer.getTokenType() ?: break
        result.add(TokenState(tokenType, lexer.getTokenStart(), lexer.getState()))
        lexer.advance()
      }
      return result
    }
  }
}

/**
 * Creates a [SyntaxLexerTestFixture] that gets a new lexer from [lexerFactory] for each check.
 * It replaces `com.intellij.testFramework.syntax.LexerTestCase`.
 *
 * The test data files are in [dirPath]. [dirPath] is absolute, or relative to the home path as `LexerTestCase.dirPath` is.
 * [expectedFileExtension] is the extension of the expected file. It starts with a dot.
 * When [checkRestart] is `false`, [SyntaxLexerTestFixture.doTest] does not call [SyntaxLexerTestFixture.checkCorrectRestart].
 * [highlighterStorageFactory] creates the storage in which the editor highlighter keeps the states of a [RestartableLexer].
 * The default is [IntBasedStorage], the storage of `LexerEditorHighlighter` for a restartable lexer.
 * Pass the factory of its own storage when the highlighter of the lexer has one.
 * Pass `null` to skip the check of the states in [SyntaxLexerTestFixture.checkCorrectRestart].
 *
 * The fixture takes the name of the current test method, so declare it as an instance property.
 * Annotate the test class with [com.intellij.testFramework.junit5.fixture.TestFixtures], or with
 * [com.intellij.testFramework.junit5.TestApplication] when the lexer needs the application.
 */
@TestOnly
@JvmOverloads
fun syntaxLexerFixture(
  dirPath: String,
  expectedFileExtension: String = ".txt",
  checkRestart: Boolean = true,
  highlighterStorageFactory: Supplier<out DataStorage>? = Supplier { IntBasedStorage() },
  lexerFactory: Supplier<out Lexer>,
): TestFixture<SyntaxLexerTestFixture> = testFixture("syntaxLexerFixture") { context ->
  val fixture = SyntaxLexerTestFixture(
    lexerFactory = lexerFactory,
    dirPath = dirPath,
    testName = lexerTestName("syntaxLexerFixture", context.extensionContext),
    expectedFileExtension = expectedFileExtension,
    checkRestart = checkRestart,
    highlighterStorageFactory = highlighterStorageFactory,
  )
  initialized(fixture) {}
}
