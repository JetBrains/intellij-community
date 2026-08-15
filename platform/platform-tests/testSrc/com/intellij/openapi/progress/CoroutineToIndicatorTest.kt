// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.platform.util.progress.ExpectedState
import com.intellij.platform.util.progress.progressReporterTest
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CoroutineToIndicatorTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertEquals(coroutineContext.job, Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())

    val modality = ModalityStateEx(listOf<Any>())

    withContext(modality.asContextElement()) {
      assertSame(modality, ModalityState.defaultModalityState())
      coroutineToIndicator {
        assertNotNull(Cancellation.currentJob())
        assertNotNull(ProgressManager.getGlobalProgressIndicator())
        assertSame(modality, ModalityState.defaultModalityState())
      }
      assertSame(modality, ModalityState.defaultModalityState())
    }

    assertEquals(coroutineContext.job, Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        coroutineToIndicator {
          ProgressManager.checkCanceled()
          coroutineContext.job.cancel()
          throw assertThrows<ProcessCanceledException> {
            ProgressManager.checkCanceled()
          }
        }
      }
    }
  }

  @Test
  @Timeout(30)
  fun `cancellation reaches detached worker through wrapped indicator`(): Unit = timeoutRunBlocking {
    val workerRegistered = CountDownLatch(1)
    val checkCancellation = CountDownLatch(1)
    val workerFinished = CountDownLatch(1)
    val workerThread = AtomicReference<Thread?>()
    val observedCancellation = AtomicReference<Throwable?>()

    val bridgeJob = launch(Dispatchers.Default) {
      coroutineToIndicator { indicator ->
        val worker = Thread({
          try {
            ProgressManager.getInstance().executeProcessUnderProgress({
              workerRegistered.countDown()
              checkCancellation.await()
              try {
                ProgressManager.checkCanceled()
              }
              catch (t: Throwable) {
                observedCancellation.set(t)
              }
            }, SensitiveProgressWrapper(indicator))
          }
          finally {
            workerFinished.countDown()
          }
        }, "coroutine-to-indicator-detached-worker")
        worker.isDaemon = true
        workerThread.set(worker)
        worker.start()
        check(workerFinished.await(5, TimeUnit.SECONDS)) { "Detached worker did not finish" }
      }
    }

    try {
      assertTrue(workerRegistered.await(5, TimeUnit.SECONDS), "Detached worker did not register under the indicator")
      bridgeJob.cancel()
      checkCancellation.countDown()
      assertTrue(workerFinished.await(5, TimeUnit.SECONDS), "Detached worker did not check cancellation")
      bridgeJob.join()

      assertInstanceOf(
        ProcessCanceledException::class.java,
        observedCancellation.get(),
        "Detached worker did not observe cancellation through the wrapped indicator",
      )
    }
    finally {
      checkCancellation.countDown()
      workerThread.get()?.join(5_000)
      bridgeJob.cancelAndJoin()
    }
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    testRunUnderIndicatorRethrow(object : Throwable() {})
    testRunUnderIndicatorRethrow(CancellationException()) // manual CE
    testRunUnderIndicatorRethrow(ProcessCanceledException()) // manual PCE
  }

  private suspend inline fun <reified T : Throwable> testRunUnderIndicatorRethrow(t: T) {
    val thrown = assertThrows<T> {
      coroutineToIndicator {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private suspend inline fun testRunUnderIndicatorRethrow(t: ProcessCanceledException) {
    val thrown = assertThrows<ProcessCanceledException> {
      coroutineToIndicator {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `delegates reporting to context reporter`() {
    progressReporterTest(
      ExpectedState(text = "Hello", details = null, fraction = null),
      ExpectedState(text = "Hello", details = "World", fraction = null),
      ExpectedState(text = "Hello", details = "World", fraction = 0.42),
      ExpectedState(text = null, details = "World", fraction = 0.42),
      ExpectedState(text = null, details = "World", fraction = null),
    ) {
      coroutineToIndicator {
        ProgressManager.progress("Hello", "World")
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator.fraction = 0.42
        indicator.text = null
        indicator.isIndeterminate = true
      }
    }
  }
}
