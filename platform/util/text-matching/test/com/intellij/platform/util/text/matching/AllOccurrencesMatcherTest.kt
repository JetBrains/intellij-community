// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.AllOccurrencesMatcher
import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.MatchingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals

class AllOccurrencesMatcherTest {
  @Test
  fun simpleCase() {
    val matcher = AllOccurrencesMatcher.create("*fooBar", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 6), TextRange(6, 12)), matcher.matchingFragments("fooBarFooBar")?.toList())
    assertEquals(listOf(TextRange(0, 6), TextRange(6, 9), TextRange(13, 16)), matcher.matchingFragments("fooBarFooBuzzBar")?.toList())
    assertEquals(listOf(TextRange(0, 6)), matcher.matchingFragments("fooBarBuzzFoo")?.toList())
    assertEquals(listOf(TextRange(0, 6), TextRange(10, 13), TextRange(17, 20)), matcher.matchingFragments("fooBarBuzzFooBuzzBar")?.toList())
    assertEquals(listOf(TextRange(0, 3), TextRange(14, 17)), matcher.matchingFragments("fooBuzzFooBuzzBar")?.toList())
  }

  @Test
  fun testCaseInsensitive() {
    val matcher = AllOccurrencesMatcher.create("*foo", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(3, 6), TextRange(6, 9)), matcher.matchingFragments("fooFooFOO")?.toList())
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), matcher.matchingFragments("FooBarfooBar")?.toList())
  }

  @Test
  fun testCaseSensitive() {
    val matcher = AllOccurrencesMatcher.create("*Foo", MatchingMode.MATCH_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), matcher.matchingFragments("FooBarFooBar")?.toList())
    assertEquals(listOf(TextRange(0, 3)), matcher.matchingFragments("FooBarfooBar")?.toList())
    assertNull(matcher.matchingFragments("fooBarFOOBar"))
  }

  @Test
  fun testFirstLetterSensitive() {
    val lowerMatcher = AllOccurrencesMatcher.create("*foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3)), lowerMatcher.matchingFragments("fooBarfooBar")?.toList())
    assertNull(lowerMatcher.matchingFragments("FooBarFooBar"))

    val upperMatcher = AllOccurrencesMatcher.create("*Foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), upperMatcher.matchingFragments("FooBarFooBar")?.toList())
    assertNull(upperMatcher.matchingFragments("fooBarfooBar"))
  }

  @Test
  fun testFirstLetterSensitiveWithWildcardLeadingSpace() {
    val lowerMatcher = AllOccurrencesMatcher.create(" foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3)), lowerMatcher.matchingFragments("fooBarfoo")?.toList())
    assertNull(lowerMatcher.matchingFragments("FooBarFoo"))

    val upperMatcher = AllOccurrencesMatcher.create(" Foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), upperMatcher.matchingFragments("FooBarFoo")?.toList())
    assertNull(upperMatcher.matchingFragments("fooBarfoo"))
  }
}