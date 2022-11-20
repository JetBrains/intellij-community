// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.Runnable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

class RunBlockingModalTest : ModalCoroutineTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      runBlockingModalContext {
        assertSame(testElement, coroutineContext[TestElementKey])
        assertSame(currentThreadContext(), EmptyCoroutineContext)
        withContext(Dispatchers.EDT) {
          assertSame(currentThreadContext(), EmptyCoroutineContext)
        }
        assertSame(currentThreadContext(), EmptyCoroutineContext)
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
      runBlockingModal {
        withContext(Dispatchers.EDT) {
          assertTrue(LaterInvocator.isInModalContext())
          val contextModality = coroutineContext.contextModality()
          assertNotEquals(ModalityState.any(), contextModality)
          assertNotEquals(ModalityState.NON_MODAL, contextModality)
          assertSame(ModalityState.current(), contextModality)
        }
      }
      assertFalse(LaterInvocator.isInModalContext())
    }
  }

  @Test
  fun dispatcher(): Unit = timeoutRunBlocking {
    runBlockingModalContext {
      assertSame(Dispatchers.Default, coroutineContext[ContinuationInterceptor])
    }
  }

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = runBlockingModalContext {
      42
    }
    assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    val t: Throwable = object : Throwable() {}
    withContext(Dispatchers.EDT) {
      val thrown = assertThrows<Throwable> {
        runBlockingModal<Unit> {
          throw t // fail the scope
        }
      }
      assertSame(t, thrown)
    }
  }

  @Test
  fun nested(): Unit = timeoutRunBlocking {
    val result = runBlockingModalContext {
      runBlockingModalContext {
        42
      }
    }
    assertEquals(42, result)
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runBlockingModalCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {}
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays default non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runBlockingModalCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT) {} // modality is not specified
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = runBlockingModalCoroutine { awaitCancellation() }
    val modalCoroutine2 = runBlockingModalCoroutine {}
    processApplicationQueue()
    assertFalse(modalCoroutine2.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val modalCoroutine = runBlockingModalCoroutine { awaitCancellation() }
      yield()
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      val modalCoroutine = runBlockingModalCoroutine { yield() }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @Test
  fun `cancelled edt coroutine is able to complete`(): Unit = timeoutRunBlocking {
    val modalEntered = Semaphore(1, 1)
    val modalCoroutine = runBlockingModalCoroutine {
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
    runBlockingModalTest {
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
        runBlockingModalTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
      }
    }
  }

  private suspend fun runBlockingModalTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      runBlockingModal(ModalTaskOwner.guess(), "") {
        val modality = requireNotNull(currentCoroutineContext().contextModality())
        assertNotEquals(modality, ModalityState.NON_MODAL)
        assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
        action()
      }
    }
  }

  private suspend fun progressManagerTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
        val modality = ModalityState.defaultModalityState()
        assertNotEquals(modality, ModalityState.NON_MODAL)
        runBlockingCancellable {
          assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
          assertSame(modality, currentCoroutineContext().contextModality())
          action()
        }
      }, "", true, null)
    }
  }
}

private fun CoroutineScope.runBlockingModalCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.EDT) {
    blockingContext {
      runBlockingModal(action)
    }
  }
}

private suspend fun <T> runBlockingModalContext(action: suspend CoroutineScope.() -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      runBlockingModal(action)
    }
  }
}

private fun <T> runBlockingModal(action: suspend CoroutineScope.() -> T): T {
  return runBlockingModal(ModalTaskOwner.guess(), "", action = action)
}
