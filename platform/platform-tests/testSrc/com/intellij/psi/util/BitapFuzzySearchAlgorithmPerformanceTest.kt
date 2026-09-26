// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.util.text.matching.BitapFuzzySearchAlgorithm
import org.junit.jupiter.api.Test

/**
 * Measures the scan of [BitapFuzzySearchAlgorithm].
 */
@StressTestApplication
@PerformanceUnitTest
class BitapFuzzySearchAlgorithmPerformanceTest {

  @Test
  fun `scan without tolerating an error`() {
    benchmarkScan("exact", "solution", MatcherBenchmarkCorpus.SYMBOL_NAMES, tolerateErrors = false)
  }

  @Test
  fun `scan while tolerating one error`() {
    benchmarkScan("tolerant", "solution", MatcherBenchmarkCorpus.SYMBOL_NAMES, tolerateErrors = true)
  }

  @Test
  fun `scan with a pattern of each length`() {
    // every name is fixed length, so only the pattern length changes
    val names = MatcherBenchmarkCorpus.SYMBOL_NAMES.map { (it + "a".repeat(80)).take(80) }
    val patternSource = "abstractpropertydeclarationsolutionprojectdocumentsymbolreferenceassembly"
    for (length in listOf(2, 16, 64)) {
      benchmarkScan("len:$length", patternSource.take(length), names, tolerateErrors = true)
    }
  }

  @Test
  fun `scan a name outside the ASCII range`() {
    benchmarkScan("non-ascii", "Объявление", MatcherBenchmarkCorpus.CYRILLIC_NAMES, tolerateErrors = true)
  }

  private fun benchmarkScan(subtest: String, pattern: String, names: List<String>, tolerateErrors: Boolean) {
    val algorithm = requireNotNull(BitapFuzzySearchAlgorithm.tryCreate(pattern)) { "'$pattern' is expected to be supported" }
    val consumer = CountingConsumer()

    benchmark.subtest(subtest, names.size) { passes ->
      consumer.checksum = 0
      repeat(passes) {
        for (name in names) algorithm.processMatches(name, tolerateErrors, consumer)
      }
      consumer.checksum
    }
  }

  /** Reads every occurrence and never stops the scan. */
  private class CountingConsumer : BitapFuzzySearchAlgorithm.MatchConsumer {
    var checksum: Int = 0

    override fun consume(startOffset: Int, endOffsetExclusive: Int, errorCount: Int): Boolean {
      checksum += startOffset + endOffsetExclusive + errorCount
      return true
    }
  }
}

private val benchmark = MatcherBenchmark(2_000_000)