// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.fus.FusReportingEventWatcher
import com.intellij.openapi.application.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class FusReportingEventWatcherTest {

  lateinit var reportingEventWatcher: FusReportingEventWatcher

  @BeforeEach
  fun obtainCleanEventWatcher() {
    reportingEventWatcher = checkNotNull(FusReportingEventWatcher.instance)
    reportingEventWatcher.reportAndFlushData { _, _, _, _, _ ->
      // force flushing current data
    }
  }

  @Test
  fun `awt dispatch time statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    repeat(1000) {
      SwingUtilities.invokeAndWait {
        Thread.sleep(1)
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, awtDispatchTimes, _, _, _ ->
        assertThat(awtDispatchTimes.totalCount).isGreaterThanOrEqualTo(1_000)
        assertThat(awtDispatchTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(1_000_000).isLessThanOrEqualTo(5_000_000)
      }
    }
  }

  @Test
  fun `awt waiting time statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    val counter = AtomicInteger(500)
    val job = Job(coroutineContext.job)
    val finishingJob = Job(coroutineContext.job)
    application.invokeLater {
      job.asCompletableFuture().join()
    }
    repeat(500) {
      application.invokeLater {
        val currentValue = counter.getAndDecrement()
        if (currentValue <= 50) {
          Thread.sleep(100)
        }
        if (currentValue <= 1) {
          finishingJob.complete()
        }
      }
    }
    delay(424.milliseconds)
    job.complete()
    finishingJob.join()

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, waitingTimes, _, _ ->
        assertThat(waitingTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(424_000_000).isLessThanOrEqualTo(500_000_000)
        assertThat(waitingTimes.getValueAtPercentile(95.0)).isGreaterThanOrEqualTo(500_000_000)
      }
    }
  }

  @Test
  fun `execution and reading lock time statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    repeat(1000) {
      withContext(Dispatchers.EDT) {
        delay(42.microseconds)
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, _, execTimes, _ ->
        assertThat(execTimes.totalCount).isGreaterThanOrEqualTo(1000)
        assertThat(execTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(42)
      }
    }
  }

  @Test
  fun `reading lock execution statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.UiWithModelAccess) {
      repeat(100) {
        runReadAction {
          Thread.sleep(12)
        }
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, _, _, lockDuration ->
        assertThat(lockDuration.readingLockExecutionTimes.totalCount).isGreaterThanOrEqualTo(100)
        assertThat(lockDuration.readingLockExecutionTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(12_000_000).isLessThanOrEqualTo(20_000_000)
      }
    }
  }

  @Test
  fun `reading lock waiting statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.UiWithModelAccess) {
      repeat(100) {
        val waStarted = Job(coroutineContext.job)
        val waEnded = Job(coroutineContext.job)
        launch(Dispatchers.Default) {
          backgroundWriteAction {
            waStarted.complete()
            waEnded.asCompletableFuture().join()
          }
        }
        waStarted.asCompletableFuture().join()
        launch(Dispatchers.Default) {
          delay(12.milliseconds)
          waEnded.complete()
        }
        runReadAction {
        }
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, _, _, lockDuration ->
        assertThat(lockDuration.readingLockWaitingTimes.totalCount).isGreaterThanOrEqualTo(100)
        assertThat(lockDuration.readingLockWaitingTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(12_000_000).isLessThanOrEqualTo(20_000_000)
        assertThat(lockDuration.readingLockExecutionTimes.getValueAtPercentile(50.0)).isLessThanOrEqualTo(1_000_000)
      }
    }
  }

  @Test
  fun `write lock execution statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.UiWithModelAccess) {
      repeat(100) {
        runWriteAction {
          Thread.sleep(12)
        }
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, _, _, lockDuration ->
        assertThat(lockDuration.writeLockExecutionTimes.totalCount).isGreaterThanOrEqualTo(100)
        assertThat(lockDuration.writeLockExecutionTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(12_000_000).isLessThanOrEqualTo(20_000_000)
      }
    }
  }

  @Test
  fun `writing lock waiting statistics`() = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.UiWithModelAccess) {
      repeat(100) {
        val raStarted = Job(coroutineContext.job)
        val raEnded = Job(coroutineContext.job)
        launch(Dispatchers.Default) {
          runReadAction {
            raStarted.complete()
            raEnded.asCompletableFuture().join()
          }
        }
        raStarted.asCompletableFuture().join()
        launch(Dispatchers.Default) {
          delay(12.milliseconds)
          raEnded.complete()
        }
        runWriteAction {
        }
      }
    }

    withContext(Dispatchers.EDT) {
      reportingEventWatcher.reportAndFlushData { _, _, _, _, lockDuration ->
        assertThat(lockDuration.writeLockWaitingTimes.totalCount).isGreaterThanOrEqualTo(100)
        assertThat(lockDuration.writeLockWaitingTimes.getValueAtPercentile(50.0)).isGreaterThanOrEqualTo(12_000_000).isLessThanOrEqualTo(20_000_000)
        assertThat(lockDuration.writeLockExecutionTimes.getValueAtPercentile(50.0)).isLessThanOrEqualTo(1_000_000)
      }
    }
  }
}