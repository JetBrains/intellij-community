// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.AllOccurrencesMatcher
import com.intellij.util.text.matching.MatchingMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AllOccurrencesMatcherTest {
  @Test
  fun simpleCase() {
    val matcher = AllOccurrencesMatcher.create("*fooBar", MatchingMode.IGNORE_CASE, "")
    assertEquals(matcher.matchingFragments("fooBarFooBar")?.toList(), listOf(TextRange(0, 6), TextRange(6, 12)));
    assertEquals(matcher.matchingFragments("fooBarFooBuzzBar")?.toList(), listOf(TextRange(0, 6), TextRange(6, 9), TextRange(13, 16)));
    assertEquals(matcher.matchingFragments("fooBarBuzzFoo")?.toList(), listOf(TextRange(0, 6)));
    assertEquals(matcher.matchingFragments("fooBarBuzzFooBuzzBar")?.toList(), listOf(TextRange(0, 6), TextRange(10, 13), TextRange(17, 20)));
    assertEquals(matcher.matchingFragments("fooBuzzFooBuzzBar")?.toList(), listOf(TextRange(0, 3), TextRange(14, 17)));
  }
}