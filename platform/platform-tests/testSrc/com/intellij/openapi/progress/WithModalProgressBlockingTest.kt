// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import com.intellij.util.timeoutRunBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.Runnable
import kotlin.coroutines.ContinuationInterceptor

/**
 * @see WithModalProgressTest
 */
class WithModalProgressBlockingTest : ModalCoroutineTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      withModalProgressBlockingContext {
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
      withModalProgressBlocking {
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
    withModalProgressBlockingContext {
      assertSame(Dispatchers.Default, coroutineContext[ContinuationInterceptor])
    }
  }

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = withModalProgressBlockingContext {
      42
    }
    assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      testWithModalProgressBlockingRethrow(object : Throwable() {})
      testWithModalProgressBlockingRethrow(CancellationException()) // manual CE
      testWithModalProgressBlockingRethrow(ProcessCanceledException()) // manual PCE
    }
  }

  private inline fun <reified T : Throwable> testWithModalProgressBlockingRethrow(t: T) {
    val thrown = assertThrows<T> {
      withModalProgressBlocking {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private fun testWithModalProgressBlockingRethrow(t: CancellationException) {
    val thrown = assertThrows<CeProcessCanceledException> {
      withModalProgressBlocking {
        throw t
      }
    }
    assertSame(t, assertInstanceOf<CancellationException>(thrown.cause))
  }

  @Test
  fun nested(): Unit = timeoutRunBlocking {
    val result = withModalProgressBlockingContext {
      withModalProgressBlockingContext {
        42
      }
    }
    assertEquals(42, result)
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = withModalProgressBlockingCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {}
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays default non-modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = withModalProgressBlockingCoroutine { awaitCancellation() }
    val nonModalCoroutine = launch(Dispatchers.EDT) {} // modality is not specified
    processApplicationQueue()
    assertFalse(nonModalCoroutine.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `modal delays modal`(): Unit = timeoutRunBlocking {
    val modalCoroutine = withModalProgressBlockingCoroutine { awaitCancellation() }
    val modalCoroutine2 = withModalProgressBlockingCoroutine {}
    processApplicationQueue()
    assertFalse(modalCoroutine2.isCompleted)
    modalCoroutine.cancel()
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val modalCoroutine = withModalProgressBlockingCoroutine { awaitCancellation() }
      yield()
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      val modalCoroutine = withModalProgressBlockingCoroutine { yield() }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @Test
  fun `cancelled edt coroutine is able to complete`(): Unit = timeoutRunBlocking {
    val modalEntered = Semaphore(1, 1)
    val modalCoroutine = withModalProgressBlockingCoroutine {
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
    withModalProgressBlockingTest {
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
        withModalProgressBlockingTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
      }
    }
  }

  private suspend fun withModalProgressBlockingTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      withModalProgressBlocking(ModalTaskOwner.guess(), "") {
        val modality = requireNotNull(currentCoroutineContext().contextModality())
        assertNotEquals(modality, ModalityState.nonModal())
        assertSame(ModalityState.nonModal(), ModalityState.defaultModalityState())
        action()
      }
    }
  }

  private suspend fun progressManagerTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
        val modality = ModalityState.defaultModalityState()
        assertNotEquals(modality, ModalityState.nonModal())
        runBlockingCancellable {
          assertSame(ModalityState.nonModal(), ModalityState.defaultModalityState())
          assertSame(modality, currentCoroutineContext().contextModality())
          action()
        }
      }, "", true, null)
    }
  }
}

private fun CoroutineScope.withModalProgressBlockingCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.EDT) {
    blockingContext {
      withModalProgressBlocking(action)
    }
  }
}

private suspend fun <T> withModalProgressBlockingContext(action: suspend CoroutineScope.() -> T): T {
  return withContext(Dispatchers.EDT) {
    blockingContext {
      withModalProgressBlocking(action)
    }
  }
}

private fun <T> withModalProgressBlocking(action: suspend CoroutineScope.() -> T): T {
  return withModalProgressBlocking(ModalTaskOwner.guess(), "", action = action)
}
