// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.management.ThreadInfo
import java.util.concurrent.atomic.AtomicInteger

/**
 * The end of the sampling loop. The freeze reporter stops its profilers there, so the profiler records no longer than the thread dumps.
 * See IJPL-258231.
 */
@TestApplication
internal class SamplingTaskTest {
  private class RecordingTask(maxDurationMs: Int, coroutineScope: CoroutineScope) :
    SamplingTask(dumpInterval = 10, maxDurationMs = maxDurationMs, coroutineScope = coroutineScope) {
    val finishedCount = AtomicInteger()

    init {
      job.start()
    }

    override suspend fun processDumpedThreads(infos: Array<ThreadInfo>) {}

    override fun samplingFinished() {
      finishedCount.incrementAndGet()
    }

    suspend fun join() {
      job.join()
    }
  }

  @Test
  fun `a loop that reaches the max duration reports its end once`(): Unit = timeoutRunBlocking {
    val task = RecordingTask(maxDurationMs = 50, coroutineScope = this)
    task.join()
    assertThat(task.finishedCount.get()).isEqualTo(1)

    task.stop()
    assertThat(task.finishedCount.get()).isEqualTo(1)
  }

  @Test
  fun `a loop that is stopped before the max duration does not report its end`(): Unit = timeoutRunBlocking {
    val task = RecordingTask(maxDurationMs = 60_000, coroutineScope = this)
    task.stopAndWait()
    assertThat(task.finishedCount.get()).isEqualTo(0)
  }
}
