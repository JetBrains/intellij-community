// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class CancellableReadActionTest {

  companion object {
    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()

    @BeforeAll
    @JvmStatic
    fun init() {
      ProgressIndicatorUtils.cancelActionsToBeCancelledBeforeWrite() // init write action listener
    }

    private fun <X> computeCancellable(action: () -> X): X {
      return ReadAction.computeCancellable<X, Nothing>(action)
    }
  }

  @Test
  fun `acquires read lock`() {
    val application = ApplicationManager.getApplication()
    application.assertReadAccessNotAllowed()
    val result = computeCancellable {
      application.assertReadAccessAllowed()
      42
    }
    assertEquals(42, result)
  }

  @Test
  fun `rethrows computation exception`() {
    testRethrow(object : Throwable() {})
  }

  @Test
  fun `rethrows computation PCE`() {
    testRethrow(ProcessCanceledException())
  }

  @Test
  fun `rethrows computation CancellationException`() {
    testRethrow(CancellationException())
  }

  @Test
  fun `rethrows computation CannotReadException`() {
    testRethrow(CannotReadException())
  }

  private inline fun <reified T : Throwable> testRethrow(t: T) {
    val thrown = assertThrows<T> {
      computeCancellable {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `read job is a child of current job`(): Unit = runBlocking {
    withJob { topLevelJob ->
      computeCancellable {
        val childJob = topLevelJob.children.single() // executeWithChildJob
        val readJob = childJob.children.single()
        assertSame(readJob, Cancellation.currentJob())
      }
    }
  }

  @Test
  fun `read job is a cancellable by outer indicator`() = runBlocking {
    val inRead = Semaphore(1)
    val indicator = EmptyProgressIndicator()
    val job = launch(Dispatchers.IO) {
      withIndicator(indicator) {
        assertThrows<CancellationException> {
          computeCancellable {
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
            inRead.up()
            throw assertThrows<JobCanceledException> {
              while (this@launch.isActive) {
                Cancellation.checkCancelled()
              }
            }
          }
        }
      }
    }
    inRead.timeoutWaitUp()
    indicator.cancel()
    job.timeoutJoin()
  }

  @Test
  fun `throws when a write is pending`(): Unit = runBlocking {
    val finishWrite = waitForPendingWrite()
    assertThrows<CannotReadException> {
      computeCancellable {
        fail()
      }
    }
    finishWrite.up()
  }

  @Test
  fun `throws when a write is running`(): Unit = runBlocking {
    val finishWrite = waitForWrite()
    assertThrows<CannotReadException> {
      computeCancellable {
        fail()
      }
    }
    finishWrite.up()
  }

  @Test
  fun `does not throw when a write is requested during almost finished computation`(): Unit = runBlocking {
    val result = computeCancellable {
      assertDoesNotThrow {
        Cancellation.checkCancelled()
      }
      waitForPendingWrite().up()
      assertThrows<ProcessCanceledException> {    // cancelled
        Cancellation.checkCancelled()
      }
      42                                          // but returning the result doesn't throw CannotReadException
    }
    assertEquals(42, result)
  }

  @Test
  fun `throws when a write is requested during computation`(): Unit = runBlocking {
    testThrowsOnWrite()
  }

  @Test
  fun `throws inside non-cancellable read action when a write is requested during computation`(): Unit = runBlocking {
    runReadAction {
      testThrowsOnWrite()
    }
  }

  private fun CoroutineScope.testThrowsOnWrite() {
    assertThrows<CannotReadException> {
      computeCancellable {
        assertDoesNotThrow {
          Cancellation.checkCancelled()
        }
        waitForPendingWrite().up()
        throw assertThrows<JobCanceledException> {
          Cancellation.checkCancelled()
        }
      }
    }
  }
}
