// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance

import com.intellij.openapi.application.UI
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

@StressTestApplication
class CoroutinesIntegrationBenchmark {

  @Test
  fun `performance of strict UI dispatcher`() {
    Assumptions.assumeTrue { System.getProperty("kotlinx.coroutines.debug") == "off" }
    Benchmark.newBenchmark("Frequent dispatch on Dispatchers.UI") {
      runUiDispatcherBenchmark()
    }
      .attempts(3)
      .warmupIterations(2)
      .start()
  }


  fun runUiDispatcherBenchmark() {
    runBlockingMaybeCancellable {
      withContext(Dispatchers.UI) {
        repeat(5_000_000) {
          yield()
        }
      }
    }
  }
}
