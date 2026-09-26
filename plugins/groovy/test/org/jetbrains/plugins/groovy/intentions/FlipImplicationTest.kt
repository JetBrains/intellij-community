// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.util.TestUtils

class FlipImplicationTest : GrIntentionTestCase("Flip '==>'") {
  override fun getBasePath(): String {
    return TestUtils.getTestDataPath() + "intentions/flipImplication/"
  }

  fun testSimple() = doTest(true)

  fun testNegatedExpression() = doTest(true)

  fun testWrapComplexExpression() = doTest(true)

  fun testUnwrapExpressionWithNegation() = doTest(true)

  fun testDoNotWrapReferenceExpression() = doTest(true)

  fun testRemoveParensFromParenthesizedExpression() = doTest(true)

  fun testPreserveWrapWithLowerPriority() = doTest(true)

  fun testDoNotWrapWithHigherPriority() = doTest(true)

  fun testWrapWithLowerPriority() = doTest(true)
}