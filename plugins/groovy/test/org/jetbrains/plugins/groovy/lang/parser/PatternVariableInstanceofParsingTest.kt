// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Test

@TestApplication
class PatternVariableInstanceofParsingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val parsing by groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "expressions/instanceof")

  @Test
  fun testNoPatternVariable(): Unit = parsing.doTest()

  @Test
  fun testSimple(): Unit = parsing.doTest()

  @Test
  fun testNewLine(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectDotCall(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectMemberAccess(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectKeyword(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectSafeMemberAccess(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectBinaryExpression(): Unit = parsing.doTest()

  @Test
  fun testNewLineRespectAssignmentStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideParentheses(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideIfStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideWhileStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideForStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideSwitchStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideDoWhileStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideSynchronizedStatement(): Unit = parsing.doTest()

  @Test
  fun testNewLineInsideTryWithResources(): Unit = parsing.doTest()

  @Test
  fun testAfterDot(): Unit = parsing.doTest()

  @Test
  fun testAfterSafeDot(): Unit = parsing.doTest()

  @Test
  fun testAfterParentheses(): Unit = parsing.doTest()

  @Test
  fun testAfterComments(): Unit = parsing.doTest()
}