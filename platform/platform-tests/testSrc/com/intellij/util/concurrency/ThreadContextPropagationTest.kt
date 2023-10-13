// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Conditions
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.getValue
import com.intellij.util.setValue
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.Runnable
import java.lang.reflect.Method
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Result
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestApplication
@ExtendWith(ThreadContextPropagationTest.Enabler::class)
class ThreadContextPropagationTest {

  class Enabler : InvocationInterceptor {

    override fun interceptTestMethod(
      invocation: InvocationInterceptor.Invocation<Void>,
      invocationContext: ReflectiveInvocationContext<Method>,
      extensionContext: ExtensionContext,
    ) {
      runWithContextPropagationEnabled {
        invocation.proceed()
      }
    }
  }

  @Test
  fun `executeOnPooledThread(Runnable)`(): Unit = timeoutRunBlocking {
    doPropagationTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
  }

  @Test
  fun `executeOnPooledThread(Callable)`(): Unit = timeoutRunBlocking {
    doPropagationTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
  }

  @Test
  fun invokeLater(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()
    doPropagationTest {
      application.invokeLater(it.runnable())
    }
    doPropagationTest {
      application.invokeLater(it.runnable(), Conditions.alwaysFalse<Nothing?>())
    }
    doPropagationTest {
      application.invokeLater(it.runnable(), ModalityState.any())
    }
    doPropagationTest {
      application.invokeLater(it.runnable(), ModalityState.any(), Conditions.alwaysFalse<Nothing?>())
    }
  }

  @Test
  fun edtExecutorService(): Unit = timeoutRunBlocking {
    val service = EdtExecutorService.getInstance()
    doExecutorServiceTest(service)
    doPropagationTest {
      service.execute(it)
    }
    doPropagationTest {
      service.execute(it)
    }
    doPropagationTest {
      service.submit(it)
    }
    doPropagationTest {
      service.submit(it.callable())
    }
  }

  @Test
  fun edtScheduledExecutorService(): Unit = timeoutRunBlocking {
    val service = EdtScheduledExecutorService.getInstance()
    doScheduledExecutorServiceTest(service)
    doPropagationTest {
      service.schedule(it.runnable(), ModalityState.any(), 10, TimeUnit.MILLISECONDS)
    }
  }

  @Test
  fun appExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.getAppExecutorService())
  }

  @Test
  fun appScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(AppExecutorUtil.getAppScheduledExecutorService())
  }

  @Test
  fun boundedApplicationPoolExecutor(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded", 1))
  }

  @Test
  fun processIOExecutor(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(ProcessIOExecutorService.INSTANCE)
  }
  @Test
  fun boundedScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled", 1))
  }

  private suspend fun doTest(submit: (() -> Unit) -> Unit) {
    val element = TestElement("element")
    withContext(element) {
      suspendCancellableCoroutine { continuation ->
        blockingContext(continuation.context) {                            // install context in calling thread
          submit {                                                         // switch to another thread
            val result: Result<Unit> = runCatching {
              assertSame(element, currentThreadContext()[TestElementKey])  // the same element must be present in another thread context
            }
            continuation.resumeWith(result)
          }
        }
      }
    }
  }

  private suspend fun doExecutorServiceTest(service: ExecutorService) {
    doPropagationTest {
      service.execute(it.runnable())
    }
    doPropagationTest {
      service.submit(it.runnable())
    }
    doPropagationTest {
      service.submit(it.callable())
    }
    doPropagationTest {
      service.invokeAny(listOf(it.callable()))
    }
    doPropagationTest {
      service.invokeAll(listOf(it.callable()))
    }
  }

  private suspend fun doScheduledExecutorServiceTest(service: ScheduledExecutorService) {
    doExecutorServiceTest(service)
    doPropagationTest {
      service.schedule(it.runnable(), 10, TimeUnit.MILLISECONDS)
    }
    doPropagationTest {
      service.schedule(it.callable(), 10, TimeUnit.MILLISECONDS)
    }
    doPropagationTest {
      val canStart = Semaphore(1)
      lateinit var future: Future<*>
      val runCounter = AtomicInteger(0)
      val runOnce = Runnable {
        canStart.timeoutWaitUp()
        when {
          runCounter.compareAndSet(0, 1) -> it()
          runCounter.compareAndSet(1, 2) -> future.cancel(false)
          else -> Assertions.fail("Cancelled periodic task must not be run again")
        }
      }
      future = service.scheduleWithFixedDelay(runOnce, 10, 10, TimeUnit.MILLISECONDS)
      canStart.up()
    }
  }

  @Test
  fun testNBRA() {
    val element = TestElement("element")
    val element2 = TestElement2("element2")
    var callTracker by AtomicReference(false)
    val callbackSemaphore = Semaphore(1)
    var cancellationTracker by AtomicReference(0)
    val uiThreadFinishSemaphore = Semaphore(1)
    var runAction by AtomicReference(false)
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test NBRA", 1)

    timeoutRunBlocking {
      val future = withContext(element) {
        blockingContext {
          ReadAction.nonBlocking(Callable {
            while (!runAction) {
              // the action should complete only when we explicitly allow it
              Thread.sleep(10)
              try {
                ProgressManager.checkCanceled()
              } catch (e : ProcessCanceledException) {
                cancellationTracker += 1
                throw e
              }
            }

            // so the read action actually was completed
            callTracker = true
            // the context of read action should be the same as the context of read action's author
            assertSame(element, currentThreadContext()[TestElementKey])
            // callback's context should not leak to read action
            assertNull(currentThreadContext()[TestElement2Key])
          })
            .finishOnUiThread(ModalityState.defaultModalityState()) {
              assertSame(element, currentThreadContext()[TestElementKey])
              assertNull(currentThreadContext()[TestElement2Key])
              uiThreadFinishSemaphore.up()
            }
            .submit(executor)
        }
      }
      withContext(element2) {
        blockingContext {
          assertNull(currentThreadContext()[TestElementKey])
          future.onSuccess {
            // callback's context belongs to the callback's owner
            assertSame(element2, currentThreadContext()[TestElement2Key])
            // callback's context does not belong to the future's owner
            assertNull(currentThreadContext()[TestElementKey])
            callbackSemaphore.up()
          }
        }
      }


      blockingContext {
        // the testing
        assertEquals(0, cancellationTracker) // not cancelled yet
        assertFalse(callTracker) // not completed yet

        ApplicationManager.getApplication().invokeAndWait {
          ApplicationManager.getApplication().runWriteAction {
            // we just cancelled NBRA by a write action
          }
        }
        assertEquals(1, cancellationTracker) // cancelled 1 time
        assertFalse(callTracker) // not completed yet

        runAction = true // read action is allowed to complete now
        while (true) {
          try {
            future.get(100, TimeUnit.MILLISECONDS)
            break
          }
          catch (_: TimeoutException) {
          }
        }

        assertEquals(1, cancellationTracker) // still cancelled 1 time
        callbackSemaphore.timeoutWaitUp()
        uiThreadFinishSemaphore.timeoutWaitUp()
      }
    }
  }

  @Test
  fun `EDT dispatcher does not capture thread context`(): Unit = timeoutRunBlocking {
    blockingContext {
      launch(Dispatchers.EDT) {
        assertNull(currentThreadContextOrNull())
      }
    }
  }

  @SystemProperty("intellij.progress.task.ignoreHeadless", "true")
  @Test
  fun `Task Modal`(): Unit = timeoutRunBlocking {
    doTest {
      object : Task.Modal(null, "", true) {
        override fun run(indicator: ProgressIndicator) {
          it()
        }
      }.queue()
    }
  }

  @SystemProperty("intellij.progress.task.ignoreHeadless", "true")
  @Test
  fun `Task Modal receives newly entered modality state in the context`(): Unit = timeoutRunBlocking {
    val finished = CompletableDeferred<Unit>()
    com.intellij.platform.ide.progress.withModalProgress(ModalTaskOwner.guess(), "", TaskCancellation.cancellable()) {
      blockingContext {
        assertSame(currentThreadContextModality(), ModalityState.defaultModalityState())
        object : Task.Modal(null, "", true) {
          override fun run(indicator: ProgressIndicator) {
            finished.completeWith(runCatching {
              assertFalse(currentThreadContextModality() == ModalityState.nonModal())
              assertSame(currentThreadContextModality(), indicator.modalityState)
              assertSame(currentThreadContextModality(), ModalityState.defaultModalityState())
            })
          }
        }.queue()
      }
    }
    finished.await()
  }

  @Test
  fun `merging update queue`() = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test", 100, true, null)
    val semaphore = Semaphore(2)
    val element = TestElement("e1")
    val element2 = TestElement2("e2")
    withContext(element) {
      blockingContext {
        queue.queue(Update.create("id") {
          assertEquals(element, currentThreadContext()[TestElementKey])
          semaphore.up()
        })
      }
      withContext(element2) {
        blockingContext {
          queue.queue(Update.create("id") {
            // no eating occurs since the contexts are different
            assertEquals(element2, currentThreadContext()[TestElement2Key])
            semaphore.up()
          })
        }
      }
    }
    semaphore.timeoutWaitUp()
  }
}
