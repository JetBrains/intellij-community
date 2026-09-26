// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class InvokeAndWaitStarvationTest {

  /**
   * [com.intellij.openapi.application.Application.invokeAndWait] parks the calling thread until the EDT picks the runnable up.
   * When the EDT is busy, and every [Dispatchers.Default] thread is parked this way, the dispatcher has nothing left to run
   * the coroutine that would eventually free the EDT -- neither the queued `invokeAndWait` callers nor the coroutine releasing
   * the EDT can make progress, and the IDE deadlocks.
   *
   * [ApplicationImpl.doInvokeAndWait] avoids this by compensating parallelism while it waits, which spawns extra pool workers.
   *
   * `BlockingSuspendingReadActionTest.pending read action do not cause thread starvation for default dispatcher`
   * covers the same scenario for a read action pending behind a write action.
   *
   * @see com.intellij.openapi.progress.util.waitWithParallelismCompensation
   */
  @Test
  @Timeout(30)
  fun `invokeAndWait does not cause thread starvation for default dispatcher`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val operationsCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2) * 2
    val startedInvocations = AtomicInteger()
    val completedInvocations = AtomicInteger()
    val edtIsOccupied = Job(coroutineContext.job)
    val edtMayProceed = Job(coroutineContext.job)
    val edtMayProceedFuture = edtMayProceed.asCompletableFuture()
    try {
      // occupy the EDT, so that every `invokeAndWait` below has to park
      SwingUtilities.invokeLater {
        edtIsOccupied.complete()
        try {
          edtMayProceedFuture.join()
        }
        catch (_: Throwable) {
          // the surrounding job was cancelled because the test has failed; the EDT must not stay blocked anyway
        }
      }
      coroutineScope {
        launch(Dispatchers.IO) {
          // `operationsCount` exceeds the parallelism of `Dispatchers.Default`, so the later callers get a thread
          // only after the earlier ones have compensated the parallelism they took away
          while (startedInvocations.get() < operationsCount) {
            delay(50.milliseconds)
          }
          // give the last callers a chance to actually park inside `invokeAndWait`
          delay(1.seconds)
          // the EDT is released from `Dispatchers.Default`: without parallelism compensation there is no thread left
          // to run this continuation
          withContext(Dispatchers.Default) {
            edtMayProceed.complete()
          }
        }
        repeat(operationsCount) {
          launch {
            edtIsOccupied.join()
            startedInvocations.incrementAndGet()
            @Suppress("ForbiddenInSuspectContextMethod") // blocking the coroutine thread is exactly what is under test
            ApplicationManager.getApplication().invokeAndWait {
              completedInvocations.incrementAndGet()
            }
          }
        }
      }
    }
    finally {
      edtMayProceed.complete()
    }
    assertEquals(operationsCount, completedInvocations.get())
  }
}
