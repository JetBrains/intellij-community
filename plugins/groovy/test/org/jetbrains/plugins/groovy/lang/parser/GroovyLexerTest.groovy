// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LexerTestCase
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyLexerTest extends LexerTestCase {

  @NotNull
  @Override
  protected Lexer createLexer() {
    new GroovyLexer()
  }

  @NotNull
  @Override
  protected String getDirPath() {
    TestUtils.testDataPath + "lexer"
  }

  @NotNull
  @Override
  protected String getTestName(boolean lowercaseFirstLetter) {
    def name = super.getTestName(lowercaseFirstLetter)
      .trim()
      .split(" ")
      .collect { it.capitalize() }
      .join("")
    return lowercaseFirstLetter ? name.uncapitalize() : name
  }

  @Override
  protected void doTest(@NotNull @NonNls String text) {
    super.doTest(text)
    checkCorrectRestart(text)
  }

  @Override
  protected String printTokens(@NotNull Lexer lexer, @NotNull CharSequence text, int start) {
    lexer.start(text, start, text.length())
    def tokens = [["offset", "state", "text", "type"]]
    def tokenType
    while ((tokenType = lexer.getTokenType()) != null) {
      tokens << [
        lexer.tokenStart.toString(),
        lexer.state.toString(),
        "'${StringUtil.escapeLineBreak(lexer.tokenText)}'".toString(),
        tokenType.toString()
      ]
      lexer.advance()
    }
    return formatTable(tokens)
  }

  private static String formatTable(List<List<String>> tokens) {
    def max = new int[tokens.first().size()]
    for (token in tokens) {
      token.eachWithIndex { column, i ->
        max[i] = Math.max(column.length(), max[i])
      }
    }
    def result = new StringBuilder()
    for (token in tokens) {
      token.eachWithIndex { column, i ->
        result.append(column.padRight(max[i] + 1))
      }
      result.append('\n')
    }
    return result.toString()
  }

  void 'test comments'() {
    doTest '''\
/**/
/***/
//
//

//


//
'''
  }
}
