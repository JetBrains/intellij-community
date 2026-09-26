// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.tools.ide.metrics.benchmark.Benchmark

/**
 * Publishes the subtests of one matcher benchmark.
 *
 * @param callsPerAttempt the number of calls one attempt makes, at least.
 */
internal class MatcherBenchmark(private val callsPerAttempt: Int) {

  /**
   * Publishes one subtest named [subtest].
   *
   * @param callsPerPass the number of calls one pass makes.
   * @param pass makes the given number of passes, and returns an accumulated result.
   */
  fun subtest(subtest: String, callsPerPass: Int, pass: (passes: Int) -> Int) {
    val passes = Math.ceilDiv(callsPerAttempt, callsPerPass)
    Benchmark.newBenchmark(subtest) {
      // the accumulated result is published, so the JIT cannot drop the calls being measured
      sink = pass(passes)
    }.startAsSubtest(subtest)
  }
}

@Volatile
private var sink: Int = 0
