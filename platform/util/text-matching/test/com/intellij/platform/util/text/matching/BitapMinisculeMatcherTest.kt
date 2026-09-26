// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.BitapMinisculeMatcher
import com.intellij.util.text.matching.MatchedFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitapMinisculeMatcherTest {

  @Test
  fun `match should find every hump of an exact camel hump pattern`() {
    assertEquals(listOf(MatchedFragment(0, 3, 0), MatchedFragment(5, 8, 0)), match("GetFoo", "GetMyFoo"))
  }

  @Test
  fun `match should reject an unrelated name`() {
    assertNull(match("GetFoo", "QuxZap"))
  }

  @Test
  fun `match should report the offsets relative to the whole name`() {
    assertEquals(listOf(MatchedFragment(6, 9, 0)), match("Bar", "GetFooBar"))
  }

  @Test
  fun `match should reject a name that misses one hump`() {
    assertNull(match("FooBar", "FooQux"))
  }

  @Test
  fun `match should return sorted fragments`() {
    for ((pattern, name) in listOf("GetFooBar" to "GetFooBar", "FooFoo" to "FooFoo", "BarFoo" to "FooBar",
                                   "FooBaz" to "FooBarBaz")) {
      val fragments = requireNotNull(match(pattern, name)) { "'$pattern' is expected to match '$name'" }
      assertTrue(fragments.zipWithNext().all { (left, right) -> left.endOffset <= right.startOffset },
                 "expected sorted disjoint fragments for '$pattern' vs '$name', got $fragments")
    }
  }

  @Test
  fun `match should return merged fragment in case matched humps are continuous`() {
    assertEquals(listOf(MatchedFragment(3, 9, 0)), match("GetFoo", "BarGetFoo"))
  }

  @Test
  fun `match should not treat a hard separator as its own hump`() {
    assertEquals(listOf(MatchedFragment(0, 3, 0), MatchedFragment(4, 7, 0)), match("Foo.Bar", "Foo.Bar"))
  }

  @Test
  fun `match should find a hump prefix at the beginning of a name hump`() {
    assertEquals(listOf(MatchedFragment(0, 2, 0), MatchedFragment(10, 12, 0)), match("IdMa", "IdentifierMatcher2"))
  }

  @Test
  fun `match should find a hump prefix of any length`() {
    for (pattern in listOf("I", "Id", "IdM", "IdMa", "IdeMat", "IdentifierMatcher")) {
      assertNotNull(match(pattern, "IdentifierMatcher2"), "'$pattern' is expected to match by hump prefixes")
    }
  }

  @Test
  fun `match should find a hump prefix while another hump is corrected`() {
    val fragments = requireNotNull(match("IdMatchr", "IdentifierMatcher2"))
    assertEquals(listOf(MatchedFragment(0, 2, 0), MatchedFragment(10, 17, 1)), fragments)
  }

  @Test
  fun `match should let a lowercase hump span several name humps`() {
    assertEquals(listOf(MatchedFragment(0, 12, 0)), match("identifierma", "IdentifierMatcher2"))
  }

  @Test
  fun `match should split an uppercase run the way it splits humps`() {
    // "FBar" is the humps "F" and "Bar", so it must match "FooBarBaz"
    assertEquals(listOf(MatchedFragment(0, 1, 0), MatchedFragment(3, 6, 0)), match("FBar", "FooBarBaz"))
    // and "FOOBar" is the humps "FOO" and "Bar"
    assertEquals(listOf(MatchedFragment(0, 6, 0)), match("FOOBar", "FOOBarBaz"))
  }

  @Test
  fun `match should ignore a leading wildcard`() {
    assertEquals(match("Bar", "GetFooBar"), match("*Bar", "GetFooBar"))
  }

  @Test
  fun `match should handle the wildcards and separators of a full pattern`() {
    // this is what DefaultChooseByNameItemProvider.buildFullPattern produces for the qualified name matcher
    assertEquals(listOf(MatchedFragment(0, 3, 0), MatchedFragment(4, 7, 0)), match("*Foo*\u0000*Bar", "Foo\u0000Bar"))
  }

  @Test
  fun `match should treat a space as a wildcard rather than a pattern char`() {
    assertEquals(listOf(MatchedFragment(0, 6, 0)), match("Get Foo", "GetFoo"))
  }

  @Test
  fun `match should tolerate a single typo in one hump`() {
    val fragments = requireNotNull(match("GetFoo", "GetFop"))
    assertEquals(1, fragments.sumOf { it.errorCount })
  }

  @Test
  fun `match should reject a name that needs a typo in two humps`() {
    assertNull(match("GetFoo", "GatFop"))
  }

  @Test
  fun `match should tolerate a typo in a pattern of one short hump`() {
    assertEquals(listOf(MatchedFragment(0, 3, 1)), match("Foo", "Fop"))
  }

  @Test
  fun `match should tolerate no typo in a short hump`() {
    // the pattern is long enough, but correcting the two char hump "Ma" would match almost anything
    assertNull(match("IdentifierMa", "IdentifierMo"))
  }

  @Test
  fun `match should tolerate two transposed chars`() {
    val fragments = requireNotNull(match("identifeirma", "IdentifierMatcher2"))
    assertEquals(listOf(MatchedFragment(0, 12, 1)), fragments)
  }

  @Test
  fun `match should reject a name shorter than the pattern`() {
    assertNull(match("IdentifierMatcher", "Id"))
  }

  @Test
  fun `match should treat a digit as its own hump`() {
    assertEquals(listOf(MatchedFragment(10, 12, 0), MatchedFragment(17, 18, 0)), match("Ma2", "IdentifierMatcher2"))
  }

  @Test
  fun `match should treat an all caps acronym as one hump`() {
    assertEquals(listOf(MatchedFragment(0, 4, 0)), match("HTTP", "HTTPServer"))
  }

  @Test
  fun `match should land a hump on a word start rather than on the first occurrence`() {
    // "get" also sits inside "Widget", and taking that occurrence would cost the score the whole word start bonus
    assertEquals(listOf(MatchedFragment(6, 9, 0)), match("Get", "WidgetGetter"))
  }

  @Test
  fun `match should return leftmost exact occurrences`() {
    assertEquals(listOf(MatchedFragment(0, 6, 0)), match("FooBar", "FooBarFooBar"))
    assertEquals(listOf(MatchedFragment(0, 3, 0), MatchedFragment(6, 9, 0)), match("FooBar", "FooFooBarBar"))
  }

  @Test
  fun `match should prefer a typo free occurrence over an earlier one with a typo`() {
    assertEquals(listOf(MatchedFragment(6, 12, 0)), match("Getter", "GutterGetter"))
  }

  @Test
  fun `match should match the humps in any order`() {
    assertEquals(listOf(MatchedFragment(0, 6, 0)), match("BarFoo", "FooBar"))
  }

  @Test
  fun `match should let a pattern hump take a word start`() {
    assertEquals(listOf(MatchedFragment(0, 3, 0), MatchedFragment(6, 9, 0)), match("fooBar", "FoobarBar"))
  }

  @Test
  fun `match should prefer an exact occurrence rather than word start with the typo`() {
    assertEquals(listOf(MatchedFragment(5, 8, 0)), match("get", "GitNuget"))
  }

  @Test
  fun `matchingDegree should score a hump on a word start above the word start bonus`() {
    assertTrue(matcher("Get").matchingDegree("WidgetGetter", false) > 1000,
               "a hump on the word start of 'WidgetGetter' is expected to earn the word start bonus")
  }

  @Test
  fun `matchingDegree should score a typo match below an exact match`() {
    val matcher = matcher("GetFoo")
    val exactDegree = matcher.matchingDegree("GetFoo", false, matcher.match("GetFoo"))
    val typoDegree = matcher.matchingDegree("GetFop", false, matcher.match("GetFop"))
    assertTrue(typoDegree < exactDegree, "typo match degree $typoDegree is expected to be below exact match degree $exactDegree")
  }

  @Test
  fun `matchingDegree should charge a typo the graded penalty`() {
    val matcher = matcher("GetFoo")
    val fragmentWithError = { errorCount: Int -> listOf(MatchedFragment(0, 6, errorCount)) }
    val cost = matcher.matchingDegree("GetFoo", false, fragmentWithError(0)) -
               matcher.matchingDegree("GetFoo", false, fragmentWithError(1))

    // one typo in the 6 char fragment "Fop", i.e. 2000 * (1 / 6) ^ 2
    assertEquals(55, cost)
  }

  @Test
  fun `matchingDegree should charge a long fragment less for the same typo`() {
    val matcher = matcher("identifierma")
    val fragmentWithError = { errorCount: Int -> listOf(MatchedFragment(0, 12, errorCount)) }
    val cost = matcher.matchingDegree("IdentifierMatcher2", false, fragmentWithError(0)) -
               matcher.matchingDegree("IdentifierMatcher2", false, fragmentWithError(1))
    // one typo in a 12 char fragment, i.e. 2000 * (1 / 12) ^ 2
    assertEquals(13, cost)
  }

  @Test
  fun `matchingDegree should charge a fragment of no length the whole weight for a typo`() {
    val matcher = matcher("GetFoo")
    val fragmentOf = { errorCount: Int -> listOf(MatchedFragment(0, 3, 0), MatchedFragment(3, 3, errorCount)) }
    val cost = matcher.matchingDegree("GetFoo", false, fragmentOf(0)) -
               matcher.matchingDegree("GetFoo", false, fragmentOf(1))
    assertEquals(2000, cost)
  }

  @Test
  fun `tryCreate should refuse a pattern with an overlong hump`() {
    assertNull(BitapMinisculeMatcher.tryCreate("a".repeat(65), HARD_SEPARATORS))
  }

  @Test
  fun `tryCreate should accept an overlong pattern split into short humps`() {
    // the limit applies to a single hump, not to the whole pattern
    assertNotNull(BitapMinisculeMatcher.tryCreate("Ab".repeat(40), HARD_SEPARATORS))
  }

  @Test
  fun `tryCreate should refuse a pattern without a meaningful hump`() {
    for (pattern in listOf("", "*", "* ", ".", "\u0000")) {
      assertNull(BitapMinisculeMatcher.tryCreate(pattern, HARD_SEPARATORS),
                 "'$pattern' carries nothing to match and is expected to be refused")
    }
  }

  @Test
  fun `pattern should keep the pattern the matcher was created with`() {
    assertEquals("*GetFoo", matcher("*GetFoo").pattern)
  }

  private fun match(pattern: String, name: String): List<MatchedFragment>? = matcher(pattern).match(name)

  private fun matcher(pattern: String): BitapMinisculeMatcher =
    requireNotNull(BitapMinisculeMatcher.tryCreate(pattern, HARD_SEPARATORS)) {
      "'$pattern' is expected to be supported"
    }

  private companion object {
    private val HARD_SEPARATORS: CharArray = charArrayOf('.', '\u0000')
  }
}
