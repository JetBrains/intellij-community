// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.ContinuationInterceptor

/**
 * @see RunWithModalProgressBlockingTest
 */
class WithModalProgressTest : ModalCoroutineTest() {

  @Test
  fun `coroutine context`(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      withModalProgress {
        assertSame(testElement, coroutineContext[TestElementKey])
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
    }
    withModalProgress {
      withContext(Dispatchers.EDT) {
        assertTrue(LaterInvocator.isInModalContext())
        val contextModality = coroutineContext.contextModality()
        assertNotEquals(ModalityState.any(), contextModality)
        assertNotEquals(ModalityState.nonModal(), contextModality)
        assertSame(ModalityState.current(), contextModality)
      }
    }
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
    }
  }

  @Test
  fun `modal context edt`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
      withModalProgress {
        withContext(Dispatchers.EDT) {
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
    val dispatcher = coroutineContext[ContinuationInterceptor]
    withModalProgress {
      assertSame(dispatcher, coroutineContext[ContinuationInterceptor])
    }
    withContext(Dispatchers.EDT) {
      withModalProgress {
        assertSame(Dispatchers.EDT, coroutineContext[ContinuationInterceptor])
      }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val limited = Dispatchers.Default.limitedParallelism(3)
    withContext(limited) {
      withModalProgress {
        assertSame(limited, coroutineContext[ContinuationInterceptor])
      }
    }
  }

  @Test
  fun `any modality`(): Unit = timeoutRunBlocking {
    suspend fun assertIllegalStateException() {
      assertThrows<IllegalStateException> {
        withModalProgress {
          fail()
        }
      }
    }
    withContext(ModalityState.any().asContextElement()) {
      assertIllegalStateException()
    }
    withContext(ModalityState.any().asContextElement() + Dispatchers.EDT) {
      assertIllegalStateException()
    }
  }

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = withModalProgress {
      42
    }
    assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    testWithModalProgressRethrow(object : Throwable() {})
    testWithModalProgressRethrowPce(CancellationException()) // manual CE
    testWithModalProgressRethrow(ProcessCanceledException()) // manual PCE
  }

  private suspend inline fun <reified T : Throwable> testWithModalProgressRethrow(t: T) {
    val thrown = assertThrows<T> {
      withModalProgress {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private suspend inline fun <reified T : Throwable> testWithModalProgressRethrowPce(t: T) {
    val thrown = assertThrows<T> {
      withModalProgress {
        throw t
      }
    }
    assertSame(t, thrown.cause)
  }

  @Test
  fun nested(): Unit = timeoutRunBlocking {
    val result = withModalProgress {
      withModalProgress {
        42
      }
    }
    assertEquals(42, result)
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = launchModalCoroutineAndWait(this)
    val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {}
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
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
    val modalCoroutine = launchModalCoroutineAndWait(this)
    val modalCoroutine2 = modalCoroutine {}
    processApplicationQueue()
    assertFalse(modalCoroutine2.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    val modalCoroutine = modalCoroutine { awaitCancellation() }
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      val modalCoroutine = launch {
        withModalProgress {
          yield()
        }
      }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @Test
  fun `cancelled edt coroutine is able to complete`(): Unit = timeoutRunBlocking {
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


  @Test
  fun `undispatched event loop outside modal progress`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContext(Dispatchers.Unconfined) {
      withModalProgress {
        withContext(Dispatchers.EDT) {
          launch(Dispatchers.EdtImmediate) { }
        }
      }
    }
  }
}

private suspend fun <T> withModalProgress(action: suspend CoroutineScope.() -> T): T {
  return com.intellij.platform.ide.progress.withModalProgress(ModalTaskOwner.guess(), "", TaskCancellation.cancellable(), action)
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
    withModalProgress(action)
  }
}
