// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.FixingLayoutMatcher
import com.intellij.psi.codeStyle.PinyinMatcher
import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.MatchedFragment
import com.intellij.util.text.matching.MatchingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals

class PinyinMatcherTest {
  @Test
  fun test() {
    val matcher = PinyinMatcher.create("*nh", FixingLayoutMatcher("*nh", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop))
    assertEquals(matcher.match("你好"), listOf(MatchedFragment(0, 2)))
    assertEquals(matcher.match("get你好"), listOf(MatchedFragment(3, 5)))
  }

  @Test
  fun test2() {
    val matcher = PinyinMatcher.create("*gh", FixingLayoutMatcher("*gh", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop))
    assertNull(matcher.match("角色"))
  }

  @Test
  fun test3() {
    val matcher = PinyinMatcher.create("*g", FixingLayoutMatcher("*g", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop))
    assertEquals(matcher.match("角色"), listOf(MatchedFragment(0, 1)))
  }
}
