// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.codeStyle.TypoTolerantMatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * todo: move to platform test module together with com.intellij.psi.codeStyle.NameUtil
 */
class MatcherBuilderTest {
  @Test
  fun testLongPatternShouldNotBeTypoTolerant() {
    val matcher = NameUtil.buildMatcher("MyLongTestClassName").typoTolerant().build()
    assertFalse(matcher is TypoTolerantMatcher)
    assertTrue(matcher.matches("MyLongTestClassName"))
  }
}