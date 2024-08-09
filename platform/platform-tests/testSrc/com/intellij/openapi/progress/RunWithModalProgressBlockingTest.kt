// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.ImplicitBlockingContextTest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.Runnable
import kotlin.coroutines.ContinuationInterceptor

/**
 * @see WithModalProgressTest
 */
@ExtendWith(ImplicitBlockingContextTest.Enabler::class)
class RunWithModalProgressBlockingTest : ModalCoroutineTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      runWithModalProgressBlockingContext {
        assertSame(testElement, coroutineContext[TestElementKey])
        assertNull(currentThreadContextOrNull())
        withContext(Dispatchers.EDT) {
          assertNull(currentThreadContextOrNull())
        }
        assertNull(currentThreadContextOrNull())
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
      runWithModalProgressBlocking {
        assertNull(currentThreadContextOrNull())
        withContext(Dispatchers.EDT) {
          assertNull(currentThreadContextOrNull())
          assertTrue(LaterInvocator.isInModalContext())
          val contextModality = coroutineContext.contextModality()
          assertNotEquals(ModalityState.any(), contextModality)
          assertNotEquals(ModalityState.nonModal(), contextModality)
          assertSame(ModalityState.current(), contextModality)
        }
      }
      assertFalse(LaterInvocator.isInModalContext())
    }
  }

  @Test
  fun dispatcher(): Unit = timeoutRunBlocking {
    runWithModalProgressBlockingContext {
      assertSame(Dispatchers.Default, coroutineContext[ContinuationInterceptor])
    }
  }

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = runWithModalProgressBlockingContext {
      42
    }
    assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      testRunWithModalProgressBlockingRethrow(object : Throwable() {})
      testRunWithModalProgressBlockingRethrowPce(CancellationException()) // manual CE
      testRunWithModalProgressBlockingRethrow(ProcessCanceledException()) // manual PCE
    }
  }

  private inline fun <reified T : Throwable> testRunWithModalProgressBlockingRethrow(t: T) {
    val thrown = assertThrows<T> {
      runWithModalProgressBlocking {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private fun testRunWithModalProgressBlockingRethrowPce(t: CancellationException) {
    val thrown = assertThrows<CeProcessCanceledException> {
      runWithModalProgressBlocking {
        throw t
      }
    }
    assertSame(t, assertInstanceOf<CancellationException>(thrown.cause.cause))
  }

  @Test
  fun `non-cancellable`(): Unit = timeoutRunBlocking {
    val job = launch(Dispatchers.EDT) {
      blockingContext {
        Cancellation.computeInNonCancelableSection<_, Nothing> {
          assertDoesNotThrow {
            runWithModalProgressBlocking {
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
  fun nested(): Unit = timeoutRunBlocking {
    val result = runWithModalProgressBlockingContext {
      runWithModalProgressBlockingContext {
        42
      }
    }
    assertEquals(42, result)
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runWithModalProgressBlockingCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {}
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays default non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runWithModalProgressBlockingCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT) {} // modality is not specified
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runWithModalProgressBlockingCoroutine { awaitCancellation() }
    val modalCoroutine2 = runWithModalProgressBlockingCoroutine {}
    processApplicationQueue()
    assertFalse(modalCoroutine2.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val modalCoroutine = runWithModalProgressBlockingCoroutine { awaitCancellation() }
      yield()
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      val modalCoroutine = runWithModalProgressBlockingCoroutine { yield() }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @Test
  fun `cancelled edt coroutine is able to complete`(): Unit = timeoutRunBlocking {
    val modalEntered = Semaphore(1, 1)
    val modalCoroutine = runWithModalProgressBlockingCoroutine {
      withContext(Dispatchers.EDT) {
        try {
          modalEntered.release()
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            // this continuation unblocks getNextEvent()
            yield()
            // this continuation ensures that yield() after getNextEvent() didn't throw CE
          }
        }
      }
    }
    modalEntered.acquire()
    processApplicationQueue()
    modalCoroutine.cancel()
  }

  @Test
  fun `current modality state is set properly`(): Unit = timeoutRunBlocking {
    runWithModalProgressBlockingTest {
      blockingContextTest()
    }
    progressManagerTest {
      blockingContextTest()
    }
  }

  private suspend fun blockingContextTest() {
    val contextModality = requireNotNull(currentCoroutineContext().contextModality())
    blockingContext {
      assertSame(contextModality, ModalityState.defaultModalityState())
      runBlockingCancellable {
        progressManagerTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
        runWithModalProgressBlockingTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
      }
    }
  }

  private suspend fun runWithModalProgressBlockingTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        val modality = requireNotNull(currentCoroutineContext().contextModality())
        assertNotEquals(modality, ModalityState.nonModal())
        assertSame(modality, ModalityState.defaultModalityState())
        action()
      }
    }
  }

  private suspend fun progressManagerTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
          val modality = ModalityState.defaultModalityState()
          assertNotEquals(modality, ModalityState.nonModal())
          runBlockingCancellable {
            assertSame(currentCoroutineContext().contextModality(), ModalityState.defaultModalityState())
            assertSame(modality, currentCoroutineContext().contextModality())
            action()
          }
        }, "", true, null)
      }
    }
  }
}

private fun CoroutineScope.runWithModalProgressBlockingCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.EDT) {
    blockingContext {
      runWithModalProgressBlocking(action)
    }
  }
}

private suspend fun <T> runWithModalProgressBlockingContext(action: suspend CoroutineScope.() -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      runWithModalProgressBlocking(action)
    }
  }
}

private fun <T> runWithModalProgressBlocking(action: suspend CoroutineScope.() -> T): T {
  return runWithModalProgressBlocking(ModalTaskOwner.guess(), "", action = action)
}
