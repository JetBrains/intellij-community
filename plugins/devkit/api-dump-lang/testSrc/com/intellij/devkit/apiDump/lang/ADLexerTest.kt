// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.devkit.apiDump.lang.lexer.ADLexer
import com.intellij.lang.impl.TokenSequence
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ADLexerTest {
  @Test
  fun testSimple() {
    assertLexing("foo-bar", "IDENTIFIER")
    assertLexing("foo", "IDENTIFIER")
    assertLexing("foo1-bar", "IDENTIFIER")
    assertLexing("-", "MINUS")
    assertLexing("*", "ASTERISK")
    assertLexing("@", "AT")
    assertLexing(":", "COLON")
    assertLexing(".", "DOT")
    assertLexing("[", "LBRACKET")
    assertLexing("]", "RBRACKET")
    assertLexing("(", "LPAREN")
    assertLexing(")", "RPAREN")
    assertLexing("<", "LESS")
    assertLexing(">", "MORE")
  }

  private fun assertLexing(text: String, vararg expectedTokens: String) {
    val tokenList = TokenSequence.performLexing(text, ADLexer())
    val tokenTypes = (0 until tokenList.tokenCount).map { tokenList.getTokenType(it).debugName }
    Assertions.assertIterableEquals(listOf(*expectedTokens), tokenTypes)
  }
}