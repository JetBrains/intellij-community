// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.tests

import com.intellij.gradle.completion.GradleDependencyCompletionFuzzyMatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class GradleDependencyCompletionFuzzyMatcherTest {

  @Test
  fun `prefixMatches always returns true regardless of name`() {
    val matcher = GradleDependencyCompletionFuzzyMatcher("no-match")
      Assertions.assertTrue(matcher.prefixMatches("something completely different"))
      Assertions.assertTrue(matcher.prefixMatches(""))
      Assertions.assertTrue(matcher.prefixMatches("org.example:artifact:1.0"))
  }

  @ParameterizedTest
  @CsvSource(
    // Case-insensitive: "JUNIT" not found case-sensitively -> fallback finds "junit" at index 4
    "JUNIT, org.junit:junit:4.13.2, 4, 9",
    // Multi-part: "junit" found but "missing" not -> fallback finds longest match "junit:" (len=6) at index 4
    "junit:missing, org.junit:junit:4.13.2, 4, 10",
    // Single part not in any segment -> fallback finds "api" (len=3) in "my-api" at index 15
    "zzz:api, org.example:my-api:1.0, 15, 18",
  )
  fun `getMatchingFragments falls back to longest substring match`(
    prefix: String, name: String, expectedStart: Int, expectedEnd: Int,
  ) {
    val matcher = GradleDependencyCompletionFuzzyMatcher(prefix)
    val fragments = matcher.getMatchingFragments(prefix, name)

      Assertions.assertEquals(1, fragments.size, "Expected exactly one matched fragment for prefix='$prefix' name='$name'")
      Assertions.assertEquals(
          expectedStart, fragments[0].startOffset,
          "Wrong start offset for prefix='$prefix' name='$name'"
      )
      Assertions.assertEquals(
          expectedEnd, fragments[0].endOffset,
          "Wrong end offset for prefix='$prefix' name='$name'"
      )
  }

  @Test
  fun `getMatchingFragments returns empty list when no substring matches anywhere`() {
    val matcher = GradleDependencyCompletionFuzzyMatcher("zzz")
    val fragments = matcher.getMatchingFragments("zzz", "org.example:artifact:1.0")
      Assertions.assertTrue(fragments.isEmpty(), "Expected no fragments when prefix cannot be matched")
  }

  @Test
  fun `getMatchingFragments succeeds without fallback when prefix matches directly`() {
    // "junit" found normally in "org.junit" — no fallback needed; result is still non-empty
    val matcher = GradleDependencyCompletionFuzzyMatcher("junit")
    val fragments = matcher.getMatchingFragments("junit", "org.junit:junit:4.13.2")
      Assertions.assertTrue(fragments.isNotEmpty(), "Expected matched fragments for direct match")
  }

  @Test
  fun `getMatchingFragments fallback excludes configuration name before parenthesis`() {
    // 'n' appears in "implementation" but NOT in the dependency coordinates after '('
    // Fallback only searches the substring starting after '(' so it returns empty
    val name = """implementation("org.example:lib:1.0")"""
    val matcher = GradleDependencyCompletionFuzzyMatcher("n")
    val fragments = matcher.getMatchingFragments("n", name)
      Assertions.assertTrue(
          fragments.isEmpty(),
          "Should not find 'n' which only appears in the configuration name, not in coordinates"
      )
  }
}