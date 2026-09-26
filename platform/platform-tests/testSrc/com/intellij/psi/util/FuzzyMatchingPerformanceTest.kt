// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy.SmithWatermanMatcher
import com.intellij.psi.codeStyle.BitapMinisculeMatcher
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.util.text.matching.MatchingMode
import org.junit.jupiter.api.Test

/**
 * Compares the four fuzzy matching implementations on one corpus.
 *
 * Work is one `match` call, plus the score where the match succeeds.
 */
@StressTestApplication
@PerformanceUnitTest
class FuzzyMatchingPerformanceTest {

  /** Easy: a small share of hits and a majority of cheap misses */
  @Test
  fun `match a prefix`() {
    benchmarkScenario("easy", "sol", MatcherBenchmarkCorpus.SYMBOL_NAMES, MATCHERS)
  }

  /** Hard: every name matching triggers advanced matching code in all implementations. */
  @Test
  fun `match a pattern that spans several humps of every name`() {
    // The pattern characters start several humps of each name.
    val names = MatcherBenchmarkCorpus.SYMBOL_NAMES.map { "PropertyProvider${it}DeclarationDecorator" }
    benchmarkScenario("hard", "ProDecl", names, MATCHERS)
  }

  /** One deletion necessary to fix a typo. */
  @Test
  fun `match a pattern that carries a typo`() {
    val typoTolerantMatchers = MATCHERS.filter { it.typoTolerant }
    benchmarkScenario("typo", "Declaraton", MatcherBenchmarkCorpus.SYMBOL_NAMES, typoTolerantMatchers)
  }

  /** Publishes one `<scenario>:<impl>` subtest per column. */
  private fun benchmarkScenario(scenario: String, pattern: String, names: List<String>, matchers: List<MatcherRunner>) {
    for (matcher in matchers) {
      val subtest = "$scenario:${matcher.label}"
      benchmark.subtest(subtest, names.size) { passes ->
        var accumulated = 0
        // a new matcher per pass, so the SmithWatermanMatcher cache never serves a name twice; every column pays it
        repeat(passes) { accumulated += matcher.onePass(pattern, names) }
        accumulated
      }
    }
  }
}

private val benchmark = MatcherBenchmark(500_000)

/**
 * One implementation under test.
 *
 * @param onePass builds a matcher, reads every name once, and returns an accumulated result.
 */
private class MatcherRunner(val label: String, val typoTolerant: Boolean, val onePass: (String, List<String>) -> Int)

private val MATCHERS: List<MatcherRunner> = listOf(
  MatcherRunner("minuscule", typoTolerant = false) { pattern, names ->
    minusculeMatchAll(NameUtil.buildMatcher(pattern).withMatchingMode(MatchingMode.IGNORE_CASE).build(), names)
  },
  MatcherRunner("typo-tolerant", typoTolerant = true) { pattern, names ->
    minusculeMatchAll(NameUtil.buildMatcher(pattern).withMatchingMode(MatchingMode.IGNORE_CASE).typoTolerant().build(), names)
  },
  MatcherRunner("bitap", typoTolerant = true) { pattern, names ->
    minusculeMatchAll(requireNotNull(BitapMinisculeMatcher.tryCreate(pattern, CharArray(0))) {
      "'$pattern' is expected to be supported"
    }, names)
  },
  MatcherRunner("smith-waterman", typoTolerant = true) { pattern, names ->
    /*
    The `smith-waterman` runner fills a new cache on every pass. Production pays that fill on the first keystroke, but
    the other implementations have no such equivalent, so recreation of stateful matcher may be more honest (or not).
     */
    val matcher = SmithWatermanMatcher(pattern)
    var accumulated = 0
    for (name in names) accumulated += matcher.match(name).score
    accumulated
  },
)

/** Matches every name, and scores the ones that match. */
private fun minusculeMatchAll(matcher: MinusculeMatcher, names: List<String>): Int {
  var accumulated = 0
  for (name in names) {
    if (matcher.match(name) != null) accumulated += matcher.matchingDegree(name)
  }
  return accumulated
}
