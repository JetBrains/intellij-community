// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.syntax

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import java.io.File
import java.io.IOException

abstract class LexerTestCase : UsefulTestCase() {
  protected abstract val dirPath: String

  protected val expectedFileExtension: String
    get() = ".txt"

  protected abstract fun createLexer(): Lexer

  fun doTest(text: String, expected: String) {
    doTest(text, expected, createLexer())
  }

  private fun doTest(text: String, expected: String? = null, lexer: Lexer = createLexer()) {
    val result = printTokens(lexer, text, 0)

    if (expected != null) {
      assertSameLines(expected, result)
    }
    else {
      assertSameLinesWithFile(getPathToTestDataFile(this.expectedFileExtension), result)
    }
  }

  protected fun printTokens(lexer: Lexer, text: CharSequence, start: Int): String {
    return printTokens(text, start, lexer)
  }

  protected fun getPathToTestDataFile(extension: String): String {
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
      val fileText = FileUtil.loadFile(File(fileName))
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

  data class Token(val type: SyntaxElementType, val start: Int, val end: Int)
}