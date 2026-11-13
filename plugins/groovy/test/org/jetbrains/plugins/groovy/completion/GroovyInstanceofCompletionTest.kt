// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyInstanceofCompletionTest {
  companion object {
    private val project = lightJavaProjectFixture()
  }

  private val groovy = groovyFixture(project, "groovy/completion/instanceof")
  private val completion by groovyCompletionFixture(groovy)

  @Test
  fun testInsideLogicalBinaryExpression() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementBody() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementBodyNegation() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionBody() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionElseBody() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionBodyNegation() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementElseBody() { completion.doVariantableTest() }

  @Test
  fun testWhileStatementBody() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testForStatementBody() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testForStatementUpdate() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementOuterScopeIsNotSupported() { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofDeclaration() { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofAssertion() { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofAssignment() { completion.doVariantableTest() }

  @Test
  fun testSwitchStatement() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testSwitchExpression() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testNoCompletionAfterSwitchExpression() { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterSwitchStatement() { completion.doVariantableTest() }

  @Test
  fun testAfterOrExpression() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testAfterAndExpressionWithNegation() { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testNoCompletionBeforeDefinitionSameNegation() { completion.doVariantableTest() }

  @Test
  fun testNoCompletionBeforeDefinitionDifferentNegation() { completion.doVariantableTest() }
}

