// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.Test

@TestApplication
class FlipImplicationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val grIntention by grIntentionFixture(projectFixture, "Flip '==>'", TestUtils.getAbsoluteTestDataPath() + "intentions/flipImplication/")

  @Test
  fun testSimple(): Unit = grIntention.doTest(true)

  @Test
  fun testNegatedExpression(): Unit = grIntention.doTest(true)

  @Test
  fun testWrapComplexExpression(): Unit = grIntention.doTest(true)

  @Test
  fun testUnwrapExpressionWithNegation(): Unit = grIntention.doTest(true)

  @Test
  fun testDoNotWrapReferenceExpression(): Unit = grIntention.doTest(true)

  @Test
  fun testRemoveParensFromParenthesizedExpression(): Unit = grIntention.doTest(true)

  @Test
  fun testPreserveWrapWithLowerPriority(): Unit = grIntention.doTest(true)

  @Test
  fun testDoNotWrapWithHigherPriority(): Unit = grIntention.doTest(true)

  @Test
  fun testWrapWithLowerPriority(): Unit = grIntention.doTest(true)
}