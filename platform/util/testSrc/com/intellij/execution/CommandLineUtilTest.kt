// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import org.junit.Assert
import org.junit.Test

class CommandLineUtilTest {
  @Test
  fun testNoNeedToQuote() {
    val commandParameter = "word"
    Assert.assertEquals("word", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSeveralWords() {
    val commandParameter = "two words"
    Assert.assertEquals("'two words'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSpecialCharacters() {
    val commandParameter = "special characters []{}()$\\.`!"
    Assert.assertEquals("'special characters []{}()$\\.`!'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSpecialCharactersWithoutSpaces() {
    val commandParameter = "[]{}()'$\\.`!\""
    Assert.assertEquals("'[]{}()'\"'\"'$\\.`!\"'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSingleQuoteInTheEnd() {
    val commandParameter = "text '"
    Assert.assertEquals("'text '\"'\"''", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testEmptyArgument() {
    val commandParameter = ""
    Assert.assertEquals("''", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testEnvVar() {
    val commandParameter = "\$FOO"
    Assert.assertEquals("'\$FOO'", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testUnicode() {
    val commandParameter = "\u0048\u0065\u006C\u006C\u006F"
    Assert.assertEquals("Hello", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testSafeUnquoted() {
    val commandParameter = "@_-+:,./"
    Assert.assertEquals("@_-+:,./", CommandLineUtil.posixQuote(commandParameter))
  }

  @Test
  fun testUnsafeUnquoted() {
    val commandParameter = "\"`\$\\\\!"
    Assert.assertEquals("'\"`\$\\\\!'", CommandLineUtil.posixQuote(commandParameter))
  }
}