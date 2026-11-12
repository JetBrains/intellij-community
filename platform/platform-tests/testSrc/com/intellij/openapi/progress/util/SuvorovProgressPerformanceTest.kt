// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.installSuvorovProgress
import com.intellij.openapi.components.service
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.application
import com.intellij.util.concurrency.TransferredWriteActionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * Measures how fast [SuvorovProgress] runs write actions that a background write action transfers to the EDT.
 *
 * The EDT waits for a lock permit in each benchmark, and the background thread transfers its write action to the EDT very frequently.
 */
@StressTestApplication
@PerformanceUnitTest
class SuvorovProgressPerformanceTest {

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "0")
  @RegistryKey("ide.suvorov.progress.kind", "[None|Bar*|Overlay|NiceOverlay]")
  fun `frequent transfer of write action to EDT in bar progress`() {
    benchmarkFrequentTransfersToEdt("Bar")
  }

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "0")
  @RegistryKey("ide.suvorov.progress.kind", "[None|Bar|Overlay*|NiceOverlay]")
  fun `frequent transfer of write action to EDT in overlay progress`() {
    benchmarkFrequentTransfersToEdt("Overlay")
  }

  @Test
  @RegistryKey("ide.suvorov.progress.showing.delay.ms", "0")
  @RegistryKey("ide.suvorov.progress.kind", "[None*|Bar|Overlay|NiceOverlay]")
  fun `frequent transfer of write action to EDT without progress UI`() {
    benchmarkFrequentTransfersToEdt("None")
  }

  private fun benchmarkFrequentTransfersToEdt(progressKind: String) {
    Assumptions.assumeTrue { installSuvorovProgress }
    Benchmark.newBenchmark("SuvorovProgress: frequent transfer of write action to EDT ($progressKind)") {
      transferWriteActionToEdtManyTimes()
    }
      .warmupIterations(1)
      .attempts(5)
      .start()
  }

  private fun transferWriteActionToEdtManyTimes(): Unit = timeoutRunBlocking(timeout = 1.minutes, context = Dispatchers.Default) {
    val transferService = application.service<TransferredWriteActionService>()
    val executedTransfers = AtomicInteger()
    val edtActionCompleted = Job(coroutineContext.job)
    backgroundWriteAction {
      launch(Dispatchers.UiWithModelAccess) {
        // the background write action blocks the EDT here, and the EDT starts SuvorovProgress
        WriteIntentReadAction.run {
          edtActionCompleted.complete()
        }
      }
      awaitSuvorovProgressOnEdt(transferService)
      repeat(TRANSFER_COUNT) {
        transferService.runOnEdtWithTransferredWriteActionAndWait {
          executedTransfers.incrementAndGet()
        }
      }
    }
    edtActionCompleted.join()
    assertThat(executedTransfers.get()).isEqualTo(TRANSFER_COUNT)
  }

  private fun awaitSuvorovProgressOnEdt(transferService: TransferredWriteActionService) {
    val onStack = AtomicBoolean()
    while (true) {
      transferService.runOnEdtWithTransferredWriteActionAndWait {
        onStack.set(SuvorovProgress.isOnStack())
      }
      if (onStack.get()) {
        return
      }
      Thread.sleep(1)
    }
  }

  private companion object {
    const val TRANSFER_COUNT = 10_000
  }
}
