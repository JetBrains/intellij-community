// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.UncaughtExceptionsExtension
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension
import javax.swing.SwingUtilities
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.resume

class CoroutineLaterInvocatorTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()
  }

  @RegisterExtension
  @JvmField
  val uncaughtExceptionsExtension = UncaughtExceptionsExtension()

  @BeforeEach
  @AfterEach
  fun checkNotModal() {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext()) {
          "Expect no modal entries. Probably some of the previous tests didn't left their entries. " +
          "Top entry is: " + LaterInvocator.getCurrentModalEntities().firstOrNull()
        }
      }
    }
  }

  private suspend fun withDifferentInitialModalities(action: suspend CoroutineScope.() -> Unit) {
    coroutineScope {
      action()
      withContext(ModalityState.any().asContextElement()) {
        action()
      }
      withContext(ModalityState.NON_MODAL.asContextElement()) {
        action()
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext())
      }
      withModalContext {
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
        withModalContext {
          assertTrue(LaterInvocator.isInModalContext())
          val contextModality = coroutineContext.contextModality()
          assertNotEquals(ModalityState.any(), contextModality)
          assertNotEquals(ModalityState.NON_MODAL, contextModality)
          assertSame(ModalityState.current(), contextModality)
        }
        assertFalse(LaterInvocator.isInModalContext())
      }
    }
  }

  @Test
  fun `dispatcher does not change`(): Unit = timeoutRunBlocking {
    ConcurrencyUtil.newSingleThreadExecutor("test").asCoroutineDispatcher().use { dispatcher ->
      withContext(dispatcher) {
        withDifferentInitialModalities {
          withModalContext {
            assertSame(dispatcher, coroutineContext[ContinuationInterceptor]) // dispatcher does not change
          }
        }
      }
    }
  }

  @Test
  fun `modal does not delay any`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(cs = this@withDifferentInitialModalities)
      val anyCoroutine = launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {}
      processSwingQueue()
      yield()
      assertTrue(anyCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `modal delays non-modal`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(this)
      val nonModalCoroutine = launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {}
      processSwingQueue()
      assertFalse(nonModalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `modal delays modal`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(cs = this@withDifferentInitialModalities)
      val modalCoroutine2 = modalCoroutine {}
      processSwingQueue()
      assertFalse(modalCoroutine2.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `any edt coroutine is resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val modalCoroutine = launch {
        withModalContext {
          awaitCancellation()
        }
      }
      processSwingQueue()
      assertFalse(modalCoroutine.isCompleted)
      modalCoroutine.cancel()
    }
  }

  @Test
  fun `non-modal edt coroutine is not resumed while modal is running`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      val modalCoroutine = launch {
        withModalContext {
          yield()
        }
      }
      yield() // this resumes in NON_MODAL
      assertTrue(modalCoroutine.isCompleted) // this line won't be executed until modalCoroutine is completed
    }
  }

  @RepeatedTest(100)
  fun `concurrent cancellation`(): Unit = timeoutRunBlocking {
    withDifferentInitialModalities {
      val modalCoroutine = launchModalCoroutineAndWait(cs = this@withDifferentInitialModalities)
      yield()
      modalCoroutine.cancel() // cancel concurrently
    }
  }

  @Test
  fun exception(): Unit = timeoutRunBlocking {
    val e = object : Throwable() {}
    withDifferentInitialModalities {
      val t = assertThrows<Throwable> {
        withModalContext {
          throw e
        }
      }
      assertSame(e, t)
    }
  }
}

private suspend fun launchModalCoroutineAndWait(cs: CoroutineScope): Job {
  val modalEntered = Semaphore(1, 1)
  val modalCoroutine = cs.modalCoroutine {
    modalEntered.release()
    awaitCancellation()
  }
  modalEntered.acquire()
  return modalCoroutine
}

private fun CoroutineScope.modalCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.Default) {
    withModalContext(action)
  }
}

private suspend fun processSwingQueue(): Unit = suspendCancellableCoroutine {
  SwingUtilities.invokeLater {
    it.resume(Unit)
  }
}
