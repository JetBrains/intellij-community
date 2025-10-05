// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser

class PatternVariableInstanceofParsingTest : GroovyParsingTestCase() {
  override fun getBasePath() = super.getBasePath() + "expressions/instanceof"

  fun testNoPatternVariable() = doTest()

  fun testSimple() = doTest()

  fun testNewLine() = doTest()

  fun testNewLineRespectDotCall() = doTest()

  fun testNewLineRespectMemberAccess() = doTest()

  fun testNewLineRespectKeyword() = doTest()

  fun testNewLineRespectSafeMemberAccess() = doTest()
}