// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.ExecutorService
import kotlin.Result
import kotlin.test.assertSame

class ThreadContextPropagationTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()

    @BeforeAll
    @JvmStatic
    fun setKey() {
      Registry.get("ide.propagate.context").setValue(true)
    }

    @AfterAll
    @JvmStatic
    fun resetKey() {
      Registry.get("ide.propagate.context").resetToDefault()
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
  fun appExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.getAppExecutorService())
  }

  @Test
  fun appScheduledExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.getAppScheduledExecutorService())
  }

  @Test
  fun boundedApplicationPoolExecutor(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded", 1))
  }

  @Test
  fun boundedScheduledExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled", 1))
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
}
