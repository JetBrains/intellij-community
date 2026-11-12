// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyInstanceofCompletionTest {
  private val groovy = groovyFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "groovy/completion/instanceof")
  private val completion by groovyCompletionFixture(groovy)

  @Test
  fun testInsideLogicalBinaryExpression() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementBody() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementBodyNegation() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionBody() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionElseBody() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testConditionalExpressionBodyNegation() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementElseBody() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testWhileStatementBody() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testForStatementBody() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testForStatementUpdate() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testIfStatementOuterScopeIsNotSupported() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofDeclaration() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofAssertion() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterInstanceofAssignment() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testSwitchStatement() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testSwitchExpression() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testNoCompletionAfterSwitchExpression() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testNoCompletionAfterSwitchStatement() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testAfterOrExpression() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testAfterAndExpressionWithNegation() = runInEdtAndWait { completion.doCompletionTest(CompletionType.BASIC) }

  @Test
  fun testNoCompletionBeforeDefinitionSameNegation() = runInEdtAndWait { completion.doVariantableTest() }

  @Test
  fun testNoCompletionBeforeDefinitionDifferentNegation() = runInEdtAndWait { completion.doVariantableTest() }
}

