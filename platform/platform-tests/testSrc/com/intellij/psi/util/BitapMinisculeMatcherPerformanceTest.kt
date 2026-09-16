// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.psi.codeStyle.BitapMinisculeMatcher
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Test

/**
 * Measures [BitapMinisculeMatcher] on the Search Everywhere session scenarios.
 */
@StressTestApplication
@PerformanceUnitTest
class BitapMinisculeMatcherPerformanceTest {

  /**
   * The cost of typing in Search Everywhere. Builds a new matcher on every keystroke and reads the whole list
   * again, so this subtest pays the construction as well.
   */
  @Test
  fun `match a pattern typed one character at a time`() {
    val names = MatcherBenchmarkCorpus.SYMBOL_NAMES
    val typedPattern = "solution"
    val patterns = (1..typedPattern.length).map { typedPattern.take(it) }
    benchmark.subtest("typing", patterns.size * names.size) { passes ->
      var matched = 0
      repeat(passes) {
        for (pattern in patterns) {
          val matcher = matcherFor(pattern)
          for (name in names) {
            if (matcher.match(name) != null) matched++
          }
        }
      }
      matched
    }
  }

  @Test
  fun `match a camel hump acronym`() {
    // acronym for AbstractPropertyDeclaration
    benchmarkMatch("acronym", "AbPrDe")
  }

  /** One deletion necessary to fix a typo. */
  @Test
  fun `match a pattern that carries a typo`() {
    benchmarkMatch("typo", "Declaraton")
  }

  @Test
  fun `reject a pattern that is in no name`() {
    benchmarkMatch("no-match", "zzqq")
  }

  @Test
  fun `matching with scoring`() {
    val matcher = matcherFor("solution")
    val names = MatcherBenchmarkCorpus.SYMBOL_NAMES
    benchmark.subtest("degree", names.size) { passes ->
      var degrees = 0
      repeat(passes) {
        for (name in names) degrees += matcher.matchingDegree(name)
      }
      degrees
    }
  }

  private fun benchmarkMatch(subtest: String, pattern: String) {
    val matcher = matcherFor(pattern)
    val names = MatcherBenchmarkCorpus.SYMBOL_NAMES
    benchmark.subtest(subtest, names.size) { passes ->
      var matched = 0
      repeat(passes) {
        for (name in names) {
          if (matcher.match(name) != null) matched++
        }
      }
      matched
    }
  }

  private fun matcherFor(pattern: String): BitapMinisculeMatcher =
    requireNotNull(BitapMinisculeMatcher.tryCreate(pattern, CharArray(0))) { "'$pattern' is expected to be supported" }
}

private val benchmark = MatcherBenchmark(2_000_000)