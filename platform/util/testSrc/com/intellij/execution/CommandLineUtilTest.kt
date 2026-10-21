// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CommandLineUtilTest {
  @Test
  fun testNoNeedToQuote() {
    val commandParameter = "word"
    Assertions.assertEquals("word", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSeveralWords() {
    val commandParameter = "two words"
    Assertions.assertEquals("'two words'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSpecialCharacters() {
    val commandParameter = "special characters []{}()$\\.`!"
    Assertions.assertEquals("'special characters []{}()$\\.`!'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSpecialCharactersWithoutSpaces() {
    val commandParameter = "[]{}()'$\\.`!\""
    Assertions.assertEquals("'[]{}()'\"'\"'$\\.`!\"'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSingleQuoteInTheEnd() {
    val commandParameter = "text '"
    Assertions.assertEquals("'text '\"'\"''", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testEmptyArgument() {
    val commandParameter = ""
    Assertions.assertEquals("''", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testEnvVar() {
    val commandParameter = "\$FOO"
    Assertions.assertEquals("'\$FOO'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testUnicode() {
    val commandParameter = "\u0048\u0065\u006C\u006C\u006F"
    Assertions.assertEquals("Hello", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSafeUnquoted() {
    val commandParameter = "@_-+:,./"
    Assertions.assertEquals("@_-+:,./", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testUnsafeUnquoted() {
    val commandParameter = "\"`\$\\\\!"
    Assertions.assertEquals("'\"`\$\\\\!'", CommandLineUtil.posixQuote(commandParameter))
  }
}