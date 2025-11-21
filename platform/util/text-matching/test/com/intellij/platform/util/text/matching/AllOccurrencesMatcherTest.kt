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
    assertEquals(listOf(TextRange(0, 6), TextRange(6, 12)), matcher.match("fooBarFooBar"))
    assertEquals(listOf(TextRange(0, 6), TextRange(6, 9), TextRange(13, 16)), matcher.match("fooBarFooBuzzBar"))
    assertEquals(listOf(TextRange(0, 6)), matcher.match("fooBarBuzzFoo"))
    assertEquals(listOf(TextRange(0, 6), TextRange(10, 13), TextRange(17, 20)), matcher.match("fooBarBuzzFooBuzzBar"))
    assertEquals(listOf(TextRange(0, 3), TextRange(14, 17)), matcher.match("fooBuzzFooBuzzBar"))
  }

  @Test
  fun testCaseInsensitive() {
    val matcher = AllOccurrencesMatcher.create("*foo", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(3, 6), TextRange(6, 9)), matcher.match("fooFooFOO"))
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), matcher.match("FooBarfooBar"))
  }

  @Test
  fun testCaseSensitive() {
    val matcher = AllOccurrencesMatcher.create("*Foo", MatchingMode.MATCH_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), matcher.match("FooBarFooBar"))
    assertEquals(listOf(TextRange(0, 3)), matcher.match("FooBarfooBar"))
    assertNull(matcher.match("fooBarFOOBar"))
  }

  @Test
  fun testFirstLetterSensitive() {
    val lowerMatcher = AllOccurrencesMatcher.create("*foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3)), lowerMatcher.match("fooBarfooBar"))
    assertNull(lowerMatcher.match("FooBarFooBar"))

    val upperMatcher = AllOccurrencesMatcher.create("*Foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), upperMatcher.match("FooBarFooBar"))
    assertNull(upperMatcher.match("fooBarfooBar"))
  }

  @Test
  fun testFirstLetterSensitiveWithWildcardLeadingSpace() {
    val lowerMatcher = AllOccurrencesMatcher.create(" foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3)), lowerMatcher.match("fooBarfoo"))
    assertNull(lowerMatcher.match("FooBarFoo"))

    val upperMatcher = AllOccurrencesMatcher.create(" Foo", MatchingMode.FIRST_LETTER, "", KeyboardLayoutConverter.noop)
    assertEquals(listOf(TextRange(0, 3), TextRange(6, 9)), upperMatcher.match("FooBarFoo"))
    assertNull(upperMatcher.match("fooBarfoo"))
  }
}