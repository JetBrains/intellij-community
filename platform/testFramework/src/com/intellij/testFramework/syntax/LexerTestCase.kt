// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.syntax

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

abstract class LexerTestCase : UsefulTestCase() {
  protected abstract val dirPath: String

  protected val expectedFileExtension: String
    get() = ".txt"

  protected abstract fun createLexer(): Lexer

  /**
   * Tokenizes [text] with [lexer] and compares the result against [expected] (if provided)
   * or a golden file derived from the test name and [expectedFileExtension].
   * Also verifies correct lexer restart via [checkCorrectRestart].
   */
  @JvmOverloads
  protected fun doTest(text: String, expected: String? = null, lexer: Lexer = createLexer()) {
    val result = printTokens(lexer, text, 0)

    if (expected != null) {
      assertSameLines(expected, result)
    }
    else {
      assertSameLinesWithFile(getPathToTestDataFile(this.expectedFileExtension), result)
    }
    if (isCheckCorrectRestart()) {
      checkCorrectRestart(text)
    }
  }

  protected fun printTokens(lexer: Lexer, text: CharSequence, start: Int): String {
    return printTokens(text, start, lexer)
  }

  protected open fun getPathToTestDataFile(extension: String): String {
    return IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/" + this.dirPath + "/" + getTestName(true) + extension
  }

  protected fun printTokens(text: String, start: Int): String {
    return printTokens(text, start, createLexer())
  }

  protected fun doFileTest(fileExt: String) {
    doTest(loadTestDataFile("." + fileExt))
  }

  protected fun loadTestDataFile(fileExt: String): String {
    val fileName = getPathToTestDataFile(fileExt)
    try {
      val fileText = Path.of(fileName).readText()
      return StringUtil.convertLineSeparators(fileText.trim { it <= ' ' })
    }
    catch (e: IOException) {
      error("can't load file " + fileName + ": " + e.message)
    }
  }

  private fun printTokens(text: CharSequence, start: Int, lexer: Lexer): String {
    lexer.start(text, start, text.length)
    val result = StringBuilder()
    while (true) {
      val tokenType = lexer.getTokenType() ?: break
      result.append(printSingleToken(text, tokenType, lexer.getTokenStart(), lexer.getTokenEnd()))
      lexer.advance()
    }
    return result.toString()
  }

  private fun printSingleToken(fileText: CharSequence, tokenType: SyntaxElementType, start: Int, end: Int): String {
    return "$tokenType ('${getTokenText(fileText, start, end)}')\n"
  }

  private fun getTokenText(sequence: CharSequence, start: Int, end: Int): String {
    return StringUtil.replace(sequence.subSequence(start, end).toString(), "\n", "\\n")
  }

  /** Whether to check correct lexer restart with [checkCorrectRestart]. Default is true. */
  protected open fun isCheckCorrectRestart() = true

  /**
   * Verifies that the lexer produces the same token sequence when restarted from any position
   * where [Lexer.getState] returns zero.
   * TODO support restartable lexers in Syntax Library
   *
   * For every such position the lexer is restarted via [Lexer.start] with the recorded offset and state,
   * and the resulting tokens are compared against the tail of the initial full-text tokenization.
   */
  protected fun checkCorrectRestart(text: String) {
    val mainLexer = createLexer()
    val allTokens = tokenize(text, 0, 0, mainLexer)
    val auxLexer = createLexer()
    auxLexer.start(text)
    var index = 0
    while (auxLexer.getTokenType() != null) {
      val state = auxLexer.getState()
      if (state == 0) {
        val tokenStart = auxLexer.getTokenStart()
        val expectedTokens = allTokens.subList(index, allTokens.size)
        val restartedTokens = tokenize(text, tokenStart, state, mainLexer)
        assertEquals(
          "Restarting impossible from offset $tokenStart `${auxLexer.getTokenText()}`\n" +
          "All tokens <type, offset, lexer state>: $allTokens\n",
          expectedTokens.joinToString("\n"),
          restartedTokens.joinToString("\n")
        )
      }
      index++
      auxLexer.advance()
    }
  }

  private fun tokenize(text: String, start: Int, state: Int, lexer: Lexer): List<TokenState> {
    val allTokens = mutableListOf<TokenState>()
    try {
      lexer.start(text, start, text.length, state)
    }
    catch (t: Throwable) {
      LOG.error("Restarting impossible from offset $start", t)
      throw RuntimeException(t)
    }
    while (true) {
      val tokenType = lexer.getTokenType() ?: break
      allTokens.add(TokenState(tokenType, lexer.getTokenStart(), lexer.getState()))
      lexer.advance()
    }
    return allTokens
  }

  data class Token(val type: SyntaxElementType, val start: Int, val end: Int)

  private data class TokenState(val type: SyntaxElementType, val offset: Int, val state: Int)
}