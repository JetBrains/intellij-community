// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

@StressTestApplication
@PerformanceUnitTest
class CoroutinesIntegrationBenchmarkTest {

  @Test
  fun `performance of strict UI dispatcher`() {
    requireDisabledCoroutineDebug()
    Benchmark.newBenchmark("Frequent dispatch on Dispatchers.UI") {
      runUiDispatcherBenchmark(Dispatchers.UI)
    }
      .attempts(3)
      .warmupIterations(2)
      .start()
  }

  @Test
  fun `performance of strict EDT dispatcher`() {
    requireDisabledCoroutineDebug()
    Benchmark.newBenchmark("Frequent dispatch on Dispatchers.EDT") {
      runUiDispatcherBenchmark(Dispatchers.EDT)
    }
      .attempts(3)
      .warmupIterations(2)
      .start()
  }


  fun runUiDispatcherBenchmark(dispatcher: CoroutineContext) {
    runBlockingMaybeCancellable {
      withContext(dispatcher) {
        repeat(5_000_000) {
          yield()
        }
      }
    }
  }

  private fun requireDisabledCoroutineDebug() {
    val debugProperty = System.getProperty("kotlinx.coroutines.debug")
    Assumptions.assumeTrue({ debugProperty == "off" }, "Existing system properties: ${System.getProperties()}")
  }
}
