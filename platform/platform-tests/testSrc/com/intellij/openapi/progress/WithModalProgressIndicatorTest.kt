// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import com.intellij.openapi.application.impl.withDifferentInitialModalities
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.ContinuationInterceptor

/**
 * @see RunBlockingModalTest
 */
class WithModalProgressIndicatorTest : ModalCoroutineTest() {

  @Test
  fun `coroutine context`(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      withDifferentInitialModalities {
        withModalProgressIndicator {
          assertSame(testElement, coroutineContext[TestElementKey])
        }
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext())
      }
      withModalProgressIndicator {
        withContext(Dispatchers.EDT) {
          assertTrue(LaterInvocator.isInModalContext())
          val contextModality = coroutineContext.contextModality()
          assertNotEquals(ModalityState.any(), contextModality)
          assertNotEquals(ModalityState.NON_MODAL, contextModality)
          assertSame(ModalityState.current(), contextModality)
        }
      }
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext())
      }
    }
  }

  @Test
  fun `modal context edt`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext())
        withModalProgressIndicator {
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
  }

  @Test
  fun dispatcher(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      withModalProgressIndicator {
        assertSame(Dispatchers.Default, coroutineContext[ContinuationInterceptor])
      }
      withContext(Dispatchers.EDT) {
        withModalProgressIndicator {
          assertSame(Dispatchers.Default, coroutineContext[ContinuationInterceptor])
        }
      }
    }
  }

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val result = withModalProgressIndicator {
        42
      }
      assertEquals(42, result)
    }
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val t: Throwable = object : Throwable() {}
      val thrown = assertThrows<Throwable> {
        withModalProgressIndicator {
          throw t // fail the scope
        }
      }
      assertSame(t, thrown)
    }
  }

  @Test
  fun nested(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val result = withModalProgressIndicator {
        withModalProgressIndicator {
          42
        }
      }
      assertEquals(42, result)
    }
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(this)
      val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {}
      processApplicationQueue()
      assertFalse(nonModalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `modal delays default non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = launchModalCoroutineAndWait(this)
    val nonModalCoroutine = launch(Dispatchers.EDT) {} // modality is not specified
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays modal`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(this)
      val modalCoroutine2 = modalCoroutine {}
      processApplicationQueue()
      assertFalse(modalCoroutine2.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val modalCoroutine = modalCoroutine { awaitCancellation() }
      yield()
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      val modalCoroutine = launch {
        withModalProgressIndicator {
          yield()
        }
      }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @Test
  fun `cancelled edt coroutine is able to complete`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalEntered = Semaphore(1, 1)
      val modalCoroutine = modalCoroutine {
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
  }
}

private suspend fun <T> withModalProgressIndicator(action: suspend CoroutineScope.() -> T): T {
  return withModalProgressIndicator(ModalTaskOwner.guess(), "", action = action)
}

private suspend fun launchModalCoroutineAndWait(cs: CoroutineScope): Job {
  val modalEntered = Semaphore(1, 1)
  val modalCoroutine = cs.modalCoroutine {
    modalEntered.release()
    awaitCancellation()
  }
  modalEntered.acquire()
  processApplicationQueue()
  return modalCoroutine
}

private fun CoroutineScope.modalCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.Default) {
    withModalProgressIndicator(action)
  }
}
