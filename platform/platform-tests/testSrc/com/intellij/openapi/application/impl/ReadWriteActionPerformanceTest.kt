// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAndBackgroundWriteActionUndispatched
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

@StressTestApplication
@PerformanceUnitTest
class ReadWriteActionPerformanceTest {

  @Test
  fun readWriteActionBenchmark() {
    val dispatcher = Dispatchers.Default.limitedParallelism(1, "test limited dispatcher")
    Benchmark.newBenchmark("undispatched readAndWriteAction") {
      runBlockingMaybeCancellable {
        val canEnd = AtomicBoolean(false)
        launch(Dispatchers.Default) {
          while (!canEnd.get()) {
            launch(Dispatchers.EDT) {
            } // constant spam with write-intents
          }
        }
        repeat(100) { _ ->
          launch(dispatcher) {
            readAndBackgroundWriteActionUndispatched {
              val currentTime = System.currentTimeMillis()
              while (System.currentTimeMillis() - currentTime < 3) {
                Cancellation.checkCancelled()
              }
              writeAction {
              }
            }
          }
        }
        canEnd.set(true)
      }
    }
      .attempts(3)
      .start()
  }
}