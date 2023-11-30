// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.platform.util.progress.impl.ProgressState
import com.intellij.platform.util.progress.progressReporter
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds

class RunBlockingCancellableTest : CancellationTest() {

  @Test
  fun `without context`() {
    assertLogThrows<IllegalStateException> {
      runBlockingCancellable {
        fail()
      }
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
        assertNull(currentThreadContextOrNull())
        assertNull(Cancellation.currentJob())
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
        assertNull(currentThreadContextOrNull())
        assertNull(Cancellation.currentJob())
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
    testRunBlockingCancellableRethrow(CancellationException()) // manual CE
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

  private fun testRunBlockingCancellableRethrow(t: CancellationException) {
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
    testRunBlockingCancellableChildFailure(ProcessCanceledException())
  }

  private inline fun <reified T : Throwable> testRunBlockingCancellableChildFailure(t: T) {
    val thrown = assertThrows<T> {
      runBlockingCancellable {
        Job(parent = coroutineContext.job).completeExceptionally(t)
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `propagates context reporter`() {
    progressReporterTest {
      val reporter = checkNotNull(progressReporter)
      assertTrue(rawProgressReporter == null)
      blockingContext {
        runBlockingCancellable {
          assertSame(reporter, progressReporter)
          assertTrue(rawProgressReporter == null)
        }
      }
    }
    progressReporterTest {
      withRawProgressReporter {
        assertTrue(progressReporter == null)
        val reporter = checkNotNull(rawProgressReporter)
        blockingContext {
          runBlockingCancellable {
            assertTrue(progressReporter == null)
            assertSame(reporter, rawProgressReporter)
          }
        }
      }
    }
  }

  @Test
  fun `delegates reporting to current indicator`() {
    val indicator = object : EmptyProgressIndicator() {
      val updates = ArrayList<ProgressState>()
      var state = ProgressState(null, null, -1.0)

      override fun setText(text: String?) {
        val newState = state.copy(text = text)
        if (newState != state) {
          state = newState
          updates.add(state)
        }
      }

      override fun setText2(text: String?) {
        val newState = state.copy(details = text)
        if (newState != state) {
          state = newState
          updates.add(state)
        }
      }

      override fun setFraction(fraction: Double) {
        val newState = state.copy(fraction = fraction)
        if (newState != state) {
          state = newState
          updates.add(state)
        }
      }
    }

    withIndicator(indicator) {
      runBlockingCancellable {
        check(progressReporter == null)
        val reporter = checkNotNull(rawProgressReporter)
        reporter.text("Hello")
        reporter.details("World")
        reporter.fraction(0.42)
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
}
