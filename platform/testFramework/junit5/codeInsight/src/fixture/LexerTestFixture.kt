// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.lang.TokenWrapper
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.lexer.RestartableLexer
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import java.io.IOException
import java.util.function.Supplier
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Lexer test helpers for a JUnit 5 test. Create the fixture with [lexerFixture].
 *
 * The fixture has the API of [com.intellij.testFramework.LexerTestCase] without the JUnit 3 base class.
 * The fixture needs no test application.
 */
@TestOnly
class LexerTestFixture internal constructor(
  private val lexerFactory: Supplier<out Lexer>,
  private val dirPath: String,
  /**
   * The name of the current test method without the `test` prefix and with a lowercase first letter.
   * The name has no leading or trailing spaces. Each other space in the name becomes an underscore.
   */
  val testName: String,
  private val expectedFileExtension: String,
  private val trimTestData: Boolean,
  private val checkRestart: Boolean,
) {
  fun createLexer(): Lexer = lexerFactory.get()

  /**
   * Tokenizes [text] and compares the tokens with [expected].
   * When [expected] is `null`, compares the tokens with the file of [getPathToTestDataFile] for the expected file extension.
   * Then calls [checkCorrectRestart], if the fixture checks the restart.
   */
  @JvmOverloads
  fun doTest(text: String, expected: String? = null) {
    doTest(text, expected, createLexer())
    if (checkRestart) {
      checkCorrectRestart(text)
    }
  }

  /**
   * Tokenizes [text] with [lexer] and compares the tokens with [expected], or with the expected file when [expected] is `null`.
   * This function does not call [checkCorrectRestart].
   */
  fun doTest(text: String, expected: String?, lexer: Lexer) {
    val result = printTokens(text, 0, lexer)
    if (expected != null) {
      assertEquals(StringUtil.convertLineSeparators(expected.trim()), StringUtil.convertLineSeparators(result.trim()))
    }
    else {
      PlatformTestUtil.assertSameLinesWithFile(getPathToTestDataFile(expectedFileExtension), result)
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
  fun getPathToTestDataFile(extension: String): String {
    val dir = if (Path(dirPath).isAbsolute) dirPath else IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/" + dirPath
    return "$dir/$testName$extension"
  }

  /**
   * Returns the text of the file of [getPathToTestDataFile] for [fileExt], with normalized line separators.
   * [fileExt] starts with a dot.
   */
  fun loadTestDataFile(fileExt: String): String {
    val fileName = getPathToTestDataFile(fileExt)
    val fileText = try {
      Path(fileName).readText()
    }
    catch (e: IOException) {
      fail("can't load file $fileName: ${e.message}")
    }
    return StringUtil.convertLineSeparators(if (trimTestData) fileText.trim() else fileText)
  }

  /**
   * Checks that the lexer state is zero on each token of [tokenTypes] in [text].
   */
  fun checkZeroState(text: String, tokenTypes: TokenSet) {
    val lexer = createLexer()
    lexer.start(text)
    while (true) {
      val type = lexer.tokenType ?: break
      if (tokenTypes.contains(type) && lexer.state != 0) {
        fail("Non-zero lexer state on token \"${lexer.tokenText}\" ($type) at ${lexer.tokenStart}")
      }
      lexer.advance()
    }
  }

  /**
   * Verifies that the lexer produces the same token sequence when restarted from any position
   * where [Lexer.getState] returns zero or [RestartableLexer.isRestartableState] returns `true`.
   *
   * For every such position the lexer is restarted via [Lexer.start] with the recorded offset and state,
   * and the resulting tokens are compared against the tail of the initial full-text tokenization.
   */
  fun checkCorrectRestart(text: String) {
    val mainLexer = createLexer()
    val allTokens = tokenize(text, 0, 0, mainLexer)
    val auxLexer = createLexer()
    auxLexer.start(text)
    var index = 0
    while (true) {
      auxLexer.tokenType ?: break
      val state = auxLexer.state
      if (state == 0 || (auxLexer is RestartableLexer && auxLexer.isRestartableState(state))) {
        val tokenStart = auxLexer.tokenStart
        val expectedTokens = allTokens.subList(index, allTokens.size)
        val restartedTokens = tokenize(text, tokenStart, state, mainLexer)
        assertEquals(expectedTokens.joinToString("\n"), restartedTokens.joinToString("\n")) {
          "Restarting impossible from offset $tokenStart - ${auxLexer.tokenText}\n" +
          "All tokens <type, offset, lexer state>: $allTokens\n"
        }
      }
      index++
      auxLexer.advance()
    }
  }

  /**
   * Verifies that the lexer produces the same token sequence when restored to any position
   * captured by [Lexer.getCurrentPosition].
   *
   * Unlike [checkCorrectRestart], which restarts the lexer via [Lexer.start] using only the integer offset and state
   * (and therefore can only test positions where the integer state is sufficient for a faithful restart),
   * this method uses [Lexer.restore] and tests *every* token position.
   *
   * This is possible because [LexerPosition] implementations may carry additional internal
   * state beyond the integer returned by [Lexer.getState] (e.g. delegate positions,
   * lookahead caches, or embedment info), allowing `restore()` to reconstruct the full
   * lexer state at any point.
   */
  fun checkCorrectRestartUsingPosition(text: String) {
    val mainLexer = createLexer()
    val allTokens = tokenize(text, 0, 0, mainLexer)
    val allPositions = buildPositions(text, mainLexer)
    for ((i, position) in allPositions.withIndex()) {
      val expectedTokens = allTokens.subList(i, allTokens.size)
      val restartedTokens = tokenize(position, mainLexer)
      mainLexer.restore(position)
      assertEquals(expectedTokens.joinToString("\n"), restartedTokens.joinToString("\n")) {
        "Restarting using position impossible from offset ${position.offset} - ${mainLexer.tokenText}\n" +
        "All tokens <type, offset, lexer state>: $allTokens\n"
      }
    }
  }

  private data class TokenState(val type: IElementType, val offset: Int, val state: Int) {
    override fun toString(): String = "TokenState[type=$type, offset=$offset, state=$state]"
  }

  companion object {
    @JvmStatic
    fun printTokens(text: CharSequence, start: Int, lexer: Lexer): String {
      lexer.start(text, start, text.length)
      val result = StringBuilder()
      while (true) {
        val tokenType = lexer.tokenType ?: break
        result.append(printSingleToken(text, tokenType, lexer.tokenStart, lexer.tokenEnd))
        lexer.advance()
      }
      return result.toString()
    }

    @JvmStatic
    fun printTokens(iterator: HighlighterIterator): String {
      val text = iterator.document.charsSequence
      val result = StringBuilder()
      while (!iterator.atEnd()) {
        result.append(printSingleToken(text, iterator.tokenType, iterator.start, iterator.end))
        iterator.advance()
      }
      return result.toString()
    }

    @JvmStatic
    fun printSingleToken(fileText: CharSequence, tokenType: IElementType, start: Int, end: Int): String {
      return "$tokenType ('${getTokenText(tokenType, fileText, start, end)}')\n"
    }

    private fun getTokenText(tokenType: IElementType, sequence: CharSequence, start: Int, end: Int): String {
      return if (tokenType is TokenWrapper) tokenType.text
      else StringUtil.replace(sequence.subSequence(start, end).toString(), "\n", "\\n")
    }

    private fun tokenize(text: String, start: Int, state: Int, lexer: Lexer): List<TokenState> {
      try {
        lexer.start(text, start, text.length, state)
      }
      catch (t: Throwable) {
        throw IllegalStateException("Restarting impossible from offset $start", t)
      }
      return collectTokens(lexer)
    }

    private fun tokenize(position: LexerPosition, lexer: Lexer): List<TokenState> {
      try {
        lexer.restore(position)
      }
      catch (t: Throwable) {
        throw IllegalStateException("Restoring location impossible from offset ${position.offset}", t)
      }
      return collectTokens(lexer)
    }

    private fun buildPositions(text: String, lexer: Lexer): List<LexerPosition> {
      try {
        lexer.start(text, 0, text.length)
      }
      catch (t: Throwable) {
        throw IllegalStateException("Restarting impossible from offset 0", t)
      }
      val result = ArrayList<LexerPosition>()
      while (lexer.tokenType != null) {
        result.add(lexer.currentPosition)
        lexer.advance()
      }
      return result
    }

    private fun collectTokens(lexer: Lexer): List<TokenState> {
      val result = ArrayList<TokenState>()
      while (true) {
        val tokenType = lexer.tokenType ?: break
        result.add(TokenState(tokenType, lexer.tokenStart, lexer.state))
        lexer.advance()
      }
      return result
    }
  }
}

/**
 * Creates a [LexerTestFixture] that gets a new lexer from [lexerFactory] for each check.
 *
 * The test data files are in [dirPath]. [dirPath] is absolute, or relative to the home path as `LexerTestCase.getDirPath` is.
 * [expectedFileExtension] is the extension of the expected file. It starts with a dot.
 * When [trimTestData] is `true`, [LexerTestFixture.loadTestDataFile] trims the text of the file.
 * When [checkRestart] is `false`, [LexerTestFixture.doTest] does not call [LexerTestFixture.checkCorrectRestart].
 *
 * The fixture takes the name of the current test method, so declare it as an instance property.
 * Annotate the test class with [com.intellij.testFramework.junit5.fixture.TestFixtures], or with
 * [com.intellij.testFramework.junit5.TestApplication] when the lexer needs the application.
 */
@TestOnly
@JvmOverloads
fun lexerFixture(
  dirPath: String,
  expectedFileExtension: String = ".txt",
  trimTestData: Boolean = true,
  checkRestart: Boolean = true,
  lexerFactory: Supplier<out Lexer>,
): TestFixture<LexerTestFixture> = testFixture("lexerFixture") { context ->
  val testMethod = context.extensionContext.testMethod.orElseThrow {
    IllegalStateException("lexerFixture needs a test method. Declare it as an instance property.")
  }
  val fixture = LexerTestFixture(
    lexerFactory = lexerFactory,
    dirPath = dirPath,
    testName = PlatformTestUtil.getTestName(testMethod.name, true).trim().replace(' ', '_'),
    expectedFileExtension = expectedFileExtension,
    trimTestData = trimTestData,
    checkRestart = checkRestart,
  )
  initialized(fixture) {}
}
