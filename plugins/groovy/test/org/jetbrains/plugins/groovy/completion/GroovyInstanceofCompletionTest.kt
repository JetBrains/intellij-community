// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyInstanceofCompletionTest : GroovyCompletionTestBase() {
  override fun getBasePath(): String = TestUtils.getTestDataPath() + "groovy/completion/instanceof"

  fun testInsideLogicalBinaryExpression() = doCompletionTest(CompletionType.BASIC)

  fun testIfStatementBody() = doCompletionTest(CompletionType.BASIC)

  fun testIfStatementBodyNegation() = doCompletionTest(CompletionType.BASIC)

  fun testConditionalExpressionBody() = doCompletionTest(CompletionType.BASIC)

  fun testConditionalExpressionElseBody() = doCompletionTest(CompletionType.BASIC)

  fun testConditionalExpressionBodyNegation() = doCompletionTest(CompletionType.BASIC)

  fun testIfStatementElseBody() = doVariantableTest()

  fun testWhileStatementBody() = doCompletionTest(CompletionType.BASIC)

  fun testForStatementBody() = doCompletionTest(CompletionType.BASIC)

  fun testForStatementUpdate() = doCompletionTest(CompletionType.BASIC)

  fun testIfStatementOuterScopeIsNotSupported() = doVariantableTest()

  fun testNoCompletionAfterInstanceofDeclaration() = doVariantableTest()

  fun testNoCompletionAfterInstanceofAssertion() = doVariantableTest()

  fun testNoCompletionAfterInstanceofAssignment() = doVariantableTest()

  fun testSwitchStatement() = doCompletionTest(CompletionType.BASIC)

  fun testSwitchExpression() = doCompletionTest(CompletionType.BASIC)

  fun testNoCompletionAfterSwitchExpression() = doVariantableTest()

  fun testNoCompletionAfterSwitchStatement() = doVariantableTest()

  fun testAfterOrExpression() = doCompletionTest(CompletionType.BASIC)

  fun testAfterAndExpressionWithNegation() = doCompletionTest(CompletionType.BASIC)

  fun testNoCompletionBeforeDefinitionSameNegation() = doVariantableTest()

  fun testNoCompletionBeforeDefinitionDifferentNegation() = doVariantableTest()
}

