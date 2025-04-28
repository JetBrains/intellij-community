// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadOverriddenContextOrNull
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.util.application
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds

class RunBlockingCancellableTest : CancellationTest() {

  @Test
  fun `without context`() {
    assertErrorLogged<IllegalStateException> {
      runBlockingCancellable {}
    }
  }

  @Test
  fun `without context non-cancellable`() {
    Cancellation.computeInNonCancelableSection<_, Nothing> {
      assertDoesNotThrow {
        runBlockingCancellable {
          yield()
        }
      }
    }
  }

  @Test
  fun `with current job context`() {
    blockingContextTest {
      val job = checkNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())

      runBlockingCancellable {
        assertJobIsChildOf(coroutineContext.job, job)
        assertNull(currentThreadOverriddenContextOrNull())
        assertEquals(coroutineContext.job, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertSame(job, Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun `with current job non-cancellable`(): Unit = timeoutRunBlocking {
    val job = launch {
      blockingContext {
        Cancellation.computeInNonCancelableSection<_, Nothing> {
          assertDoesNotThrow {
            runBlockingCancellable {
              @OptIn(ExperimentalCoroutinesApi::class)
              assertNull(coroutineContext.job.parent) // rbc does not attach to blockingContext job
              assertDoesNotThrow {
                ensureActive()
              }
              this@launch.cancel()
              assertDoesNotThrow {
                ensureActive()
              }
            }
          }
        }
      }
    }
    job.join()
    assertTrue(job.isCancelled)
  }

  @Test
  fun `with indicator context`() {
    indicatorTest {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())

      runBlockingCancellable {
        assertNull(currentThreadOverriddenContextOrNull())
        assertEquals(coroutineContext.job, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun `with indicator non-cancellable context`() {
    val modalityState = ModalityStateEx()
    withIndicator(EmptyProgressIndicator(modalityState)) {
      ProgressManager.getInstance().computeInNonCancelableSection<_, Nothing> {
        assertSame(modalityState, ProgressManager.getInstance().currentProgressModality)
        runBlockingCancellable {}
        assertSame(modalityState, ProgressManager.getInstance().currentProgressModality) // IDEA-325853
      }
    }
  }

  @Test
  fun `with indicator cancellation`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      assertThrows<ProcessCanceledException> {
        runBlockingCancellable {
          assertDoesNotThrow {
            ensureActive()
          }
          indicator.cancel()
          awaitCancellation()
        }
      }
    }
  }

  @Test
  fun `with indicator non-cancellable`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      Cancellation.computeInNonCancelableSection<_, Nothing> {
        assertDoesNotThrow {
          runBlockingCancellable {
            @OptIn(ExperimentalCoroutinesApi::class)
            assertNull(coroutineContext.job.parent) // rbc does not attach to blockingContext job
            assertDoesNotThrow {
              ensureActive()
            }
            indicator.cancel()
            delay(100.milliseconds) // let indicator polling job kick in
            assertDoesNotThrow {
              ensureActive()
            }
          }
        }
      }
    }
  }

  @Test
  fun `with indicator under job non-cancellable`(): Unit = timeoutRunBlocking {
    launch {
      blockingContext {
        indicatorTest {
          Cancellation.computeInNonCancelableSection<_, Nothing> {
            assertDoesNotThrow {
              runBlockingCancellable {
                @OptIn(ExperimentalCoroutinesApi::class)
                assertNull(coroutineContext.job.parent) // rbc does not attach to blockingContext job
                assertDoesNotThrow {
                  ensureActive()
                }
                this@launch.cancel()
                delay(100.milliseconds) // let indicator polling job kick in
                assertDoesNotThrow {
                  ensureActive()
                }
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `with current job rethrows exceptions`() {
    blockingContextTest {
      testRunBlockingCancellableRethrow()
    }
  }

  @Test
  fun `with indicator rethrows exceptions`() {
    indicatorTest {
      testRunBlockingCancellableRethrow()
    }
  }

  private fun testRunBlockingCancellableRethrow() {
    testRunBlockingCancellableRethrow(object : Throwable() {})
    testRunBlockingCancellableRethrowPce(CancellationException()) // manual CE
    testRunBlockingCancellableRethrow(ProcessCanceledException()) // manual PCE
  }

  private inline fun <reified T : Throwable> testRunBlockingCancellableRethrow(t: T) {
    val thrown = assertThrows<T> {
      runBlockingCancellable {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private fun testRunBlockingCancellableRethrowPce(t: CancellationException) {
    val thrown = assertThrows<CeProcessCanceledException> {
      runBlockingCancellable {
        throw t
      }
    }
    assertSame(t, thrown.cause)
  }

  @Test
  fun `with current job child failure`() {
    blockingContextTest {
      testRunBlockingCancellableChildFailure()
    }
  }

  @Test
  fun `with indicator child failure`() {
    indicatorTest {
      testRunBlockingCancellableChildFailure()
    }
  }

  private fun testRunBlockingCancellableChildFailure() {
    testRunBlockingCancellableChildFailure(object : Throwable() {})
    testRunBlockingCancellableChildDoesNotFailParent(CancellationException())
    testRunBlockingCancellableChildDoesNotFailParent(ProcessCanceledException())
  }

  private inline fun <reified T : Throwable> testRunBlockingCancellableChildFailure(t: T) {
    val thrown = assertThrows<T> {
      runBlockingCancellable {
        Job(parent = coroutineContext.job).completeExceptionally(t)
      }
    }
    assertSame(t, thrown)
  }

  private fun testRunBlockingCancellableChildDoesNotFailParent(t: Throwable) {
    assertDoesNotThrow {
      runBlockingCancellable {
        Job(parent = coroutineContext.job).completeExceptionally(t)
      }
    }
  }

  @Test
  fun `two neighbor calls the same thread restore context properly`(): Unit = timeoutRunBlocking {
    launch {
      blockingContext {
        runBlockingCancellable {} // 2
      }
    }
    blockingContext {
      runBlockingCancellable { // 1
        yield()
        // Will pump the queue and run previously launched coroutine.
        // That coroutine runs inner runBlockingCancellable which installs its own thread context.
        // Then inner runBlockingCancellable pumps the same queue, and gets to this continuation.
        // This continuation tries to restore its context.
        // We get the following incorrect chain:
        // set context 1
        // -> set context 2
        // -> reset context to whatever was before 1
        // -> reset context to whatever was before 2 (i.e. 1)
      }
    }
  }

  @Test
  @RegistryKey("ide.run.blocking.cancellable.assert.in.tests", "true")
  fun `runBlockingCancellable is not allowed in wa`(): Unit = timeoutRunBlocking {
    edtWriteAction {
      assertErrorLogged<java.lang.IllegalStateException> {
        runBlockingCancellable {
        }
      }
    }
  }

  @Test
  @RegistryKey("ide.run.blocking.cancellable.assert.in.tests", "true")
  fun `runBlockingCancellable in inner explicit ra of wa`(): Unit = timeoutRunBlocking {
    backgroundWriteAction {
      ReadAction.run<Throwable> {
        // checks that there are no assertions
        runBlockingCancellable {
        }
      }
    }
  }
}
