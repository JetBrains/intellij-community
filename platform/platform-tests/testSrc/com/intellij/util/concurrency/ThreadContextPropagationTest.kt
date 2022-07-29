// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.openapi.progress.timeoutWaitUp
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class ThreadContextPropagationTest {

  companion object {

    @RegisterExtension
    @JvmField
    val propagationExtension: InvocationInterceptor = object : InvocationInterceptor {

      override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
      ) {
        Propagation.runWithContextPropagationEnabled {
          invocation.proceed()
        }
      }
    }
  }

  @Test
  fun `executeOnPooledThread(Runnable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
  }

  @Test
  fun `executeOnPooledThread(Callable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
  }

  @Test
  fun invokeLater(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()
    doTest {
      application.invokeLater(it.runnable())
    }
    doTest {
      application.invokeLater(it.runnable(), Conditions.alwaysFalse<Nothing?>())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any(), Conditions.alwaysFalse<Nothing?>())
    }
  }

  @Test
  fun edtExecutorService(): Unit = timeoutRunBlocking {
    val service = EdtExecutorService.getInstance()
    doExecutorServiceTest(service)
    doTest {
      service.execute(it, ModalityState.any())
    }
    doTest {
      service.execute(it, ModalityState.any(), Conditions.alwaysFalse<Nothing?>())
    }
    doTest {
      service.submit(it, ModalityState.any())
    }
    doTest {
      service.submit(it.callable(), ModalityState.any())
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
  fun boundedScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled", 1))
  }

  private suspend fun doTest(submit: (() -> Unit) -> Unit) {
    return suspendCancellableCoroutine { continuation ->
      val element = TestElement("element")
      withThreadContext(element) {                                       // install context in calling thread
        submit {                                                         // switch to another thread
          val result: Result<Unit> = runCatching {
            assertSame(element, currentThreadContext()[TestElementKey])  // the same element must be present in another thread context
          }
          continuation.resumeWith(result)
        }
      }
    }
  }

  private suspend fun doExecutorServiceTest(service: ExecutorService) {
    doTest {
      service.execute(it.runnable())
    }
    doTest {
      service.submit(it.runnable())
    }
    doTest {
      service.submit(it.callable())
    }
    doTest {
      service.invokeAny(listOf(it.callable()))
    }
    doTest {
      service.invokeAll(listOf(it.callable()))
    }
  }

  private suspend fun doScheduledExecutorServiceTest(service: ScheduledExecutorService) {
    doExecutorServiceTest(service)
    doTest {
      service.schedule(it.runnable(), 10, TimeUnit.MILLISECONDS)
    }
    doTest {
      service.schedule(it.callable(), 10, TimeUnit.MILLISECONDS)
    }
    doTest {
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
}
