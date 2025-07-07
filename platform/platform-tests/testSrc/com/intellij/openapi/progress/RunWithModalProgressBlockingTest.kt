// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadOverriddenContextOrNull
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.ModalCoroutineTest
import com.intellij.openapi.application.impl.processApplicationQueue
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.application
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds

/**
 * @see WithModalProgressTest
 */
class RunWithModalProgressBlockingTest : ModalCoroutineTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    val testElement = TestElement("xx")
    withContext(testElement) {
      runWithModalProgressBlockingContext {
        assertSame(testElement, coroutineContext[TestElementKey])
        assertNull(currentThreadOverriddenContextOrNull())
        withContext(Dispatchers.EDT) {
          assertNull(currentThreadOverriddenContextOrNull())
        }
        assertNull(currentThreadOverriddenContextOrNull())
      }
    }
  }

  @Test
  fun `modal context`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertFalse(LaterInvocator.isInModalContext())
      runWithModalProgressBlocking {
        assertNull(currentThreadOverriddenContextOrNull())
        withContext(Dispatchers.EDT) {
          assertNull(currentThreadOverriddenContextOrNull())
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

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `background wa is permitted`(): Unit = timeoutRunBlocking {
    // we test the absence of deadlocks here
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        runWithModalProgressBlocking {
          backgroundWriteAction {
          }
        }
      }
    }
  }


  private suspend fun blockingContextTest() {
    val contextModality = requireNotNull(currentCoroutineContext().contextModality())
    assertSame(contextModality, ModalityState.defaultModalityState())
    runBlockingCancellable {
      progressManagerTest {
        val nestedModality = currentCoroutineContext().contextModality()
        assertSame(nestedModality, ModalityState.defaultModalityState())
      }
      runWithModalProgressBlockingTest {
        val nestedModality = currentCoroutineContext().contextModality()
        assertSame(nestedModality, ModalityState.defaultModalityState())
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

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `simultaneous wa and wa are forbidden`(): Unit = timeoutRunBlocking(timeout = 30.seconds, context = Dispatchers.EDT) {
    val writeActionCounter = AtomicInteger(0)
    writeIntentReadAction {
      runWithModalProgressBlocking {
        repeat(Runtime.getRuntime().availableProcessors() * 10) {
          launch(Dispatchers.Default) {
            backgroundWriteAction {
              try {
                writeActionCounter.incrementAndGet()
                assertEquals(1, writeActionCounter.get())
                Thread.sleep(100)
              }
              finally {
                writeActionCounter.decrementAndGet()
              }
            }
          }
        }
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `simultaneous wa and ra are forbidden`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val writeActionCounter = AtomicInteger(0)
    writeIntentReadAction {
      runWithModalProgressBlocking {
        repeat(Runtime.getRuntime().availableProcessors() * 5) {
          launch(Dispatchers.Default) {
            backgroundWriteAction {
              try {
                writeActionCounter.incrementAndGet()
                Thread.sleep(100)
              }
              finally {
                writeActionCounter.decrementAndGet()
              }
            }
          }
        }
        repeat(Runtime.getRuntime().availableProcessors() * 5) {
          launch(Dispatchers.Default) {
            ApplicationManager.getApplication().runReadAction {
              assertEquals(0, writeActionCounter.get())
              Thread.sleep(100)
            }
          }
        }
      }
    }
  }

  @Test
  fun `simultaneous wa and attempting ra are forbidden`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val inWriteAction = AtomicBoolean(false)
    writeIntentReadAction {
      runWithModalProgressBlocking {
        val job = Job()
        launch(Dispatchers.EDT) {
          edtWriteAction {
            try {
              inWriteAction.set(true)
              job.complete()
              Thread.sleep(1000)
            }
            finally {
              inWriteAction.set(false)
            }
          }
        }
        job.asCompletableFuture().join()
        assertFalse((ApplicationManager.getApplication() as ApplicationEx).tryRunReadAction {
          fail<Nothing>("RA should not start")
        })
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `simultaneous wa and attempting ra are forbidden 2`(): Unit = runBlocking(Dispatchers.EDT) {
    val writeActionCounter = AtomicInteger(0)
    writeIntentReadAction {
      runWithModalProgressBlocking {
        repeat(Runtime.getRuntime().availableProcessors() * 5) {
          launch(Dispatchers.Default) {
            backgroundWriteAction {
              try {
                writeActionCounter.incrementAndGet()
                Thread.sleep(100)
              }
              finally {
                writeActionCounter.decrementAndGet()
              }
            }
          }
        }
        repeat(Runtime.getRuntime().availableProcessors() * 5) {
          launch(Dispatchers.Default) {
            (ApplicationManager.getApplication() as ApplicationEx).tryRunReadAction {
              assertEquals(0, writeActionCounter.get())
              Thread.sleep(100)
            }
          }
        }
      }
    }
  }


  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `simultaneous wa and wira are forbidden`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val writeActionCounter = AtomicInteger(0)
    writeIntentReadAction {
      runWithModalProgressBlocking {
        repeat(200) {
          launch(Dispatchers.Default) {
            backgroundWriteAction {
              try {
                writeActionCounter.incrementAndGet()
                Thread.sleep(10)
              }
              finally {
                writeActionCounter.decrementAndGet()
              }
            }
          }
        }
        repeat(100) {
          launch(Dispatchers.Default) {
            writeIntentReadAction {
              assertEquals(0, writeActionCounter.get())
              Thread.sleep(10)
            }
          }
        }
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `service initializer inherits read lock from context`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    runWithModalProgressBlocking {
      ReadAction.nonBlocking<Unit> {
        application.service<MyTestService>()
      }.executeSynchronously()
    }
  }

  @Service
  private class MyTestService {
    init {
      assertTrue { application.isReadAccessAllowed }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `RA and WA are mutually exclusive inside modal progress`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val writeCoroutineStarted = Job()
    val writeActionCanStart = Job()

    val inWaCounter = AtomicBoolean(false)

    writeIntentReadAction {
      runWithModalProgressBlocking {
        launch(Dispatchers.EDT) {
          writeCoroutineStarted.complete()
          writeActionCanStart.join()
          edtWriteAction {
            inWaCounter.set(true)
          }
        }
        ReadAction.nonBlocking<Unit> {
          writeCoroutineStarted.asCompletableFuture().join()
          writeActionCanStart.complete()
          Thread.sleep(100)
          assertFalse(inWaCounter.get())
        }.executeSynchronously()
      }
    }
  }

  @Test
  fun `read access is not allowed by default within modal progress`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    writeIntentReadAction {
      runWithModalProgressBlocking {
        assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
        readAction {
          assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
        }
      }
    }
  }

  @Test
  fun `write intent read access is not allowed by default within modal progress`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    writeIntentReadAction {
      runWithModalProgressBlocking {
        assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired)
        withContext(Dispatchers.EDT) {
          assertTrue(ApplicationManager.getApplication().isWriteIntentLockAcquired)
        }
      }
    }
  }

  @Test
  fun `pure read access in explicit read action`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    runWithModalProgressBlocking {
      ApplicationManager.getApplication().runReadAction {
        assertFalse(application.isWriteIntentLockAcquired)
        assertTrue(application.holdsReadLock())
        assertFalse(application.isWriteAccessAllowed)
        assertTrue(application.isReadAccessAllowed)
      }
    }
  }
}

private fun CoroutineScope.runWithModalProgressBlockingCoroutine(action: suspend CoroutineScope.() -> Unit): Job {
  return launch(Dispatchers.EDT) {
    runWithModalProgressBlocking(action)
  }
}

private suspend fun <T> runWithModalProgressBlockingContext(action: suspend CoroutineScope.() -> T): T {
  return withContext(Dispatchers.EDT) {
    runWithModalProgressBlocking(action)
  }
}

private fun <T> runWithModalProgressBlocking(action: suspend CoroutineScope.() -> T): T {
  return runWithModalProgressBlocking(ModalTaskOwner.guess(), "", action = action)
}
