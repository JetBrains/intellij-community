// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicInteger

private const val REPETITIONS = 100

@TestApplication
@ExtendWith(ThreadContextPropagationTest.Enabler::class)
class CurrentThreadCoroutineScopeTest {

  @Test
  fun `capturing of thread context`(): Unit = timeoutRunBlocking {
    val semaphore = Semaphore(1)
    val id = "abcde"
    withContext(TestElement(id)) {
      blockingContext {
        currentThreadCoroutineScope().launch {
          assertEquals(id, coroutineContext[TestElementKey]?.value)
          semaphore.up()
        }
      }
    }
    semaphore.timeoutWaitUp()
  }

  @Test
  fun `no thread scope when there is no job`(): Unit = timeoutRunBlocking {
    val id = "abcde"
    withContext(TestElement(id)) {
      blockingContext {
        ApplicationManager.getApplication().executeOnPooledThread {
          assertLogThrows<IllegalStateException> { currentThreadCoroutineScope() }
        }
      }
    }
  }

  @Test
  fun `allow scope when structured concurrency is enabled`(): Unit = timeoutRunBlocking {
    val id = "abcde"
    withContext(TestElement(id)) {
      blockingContextScope {
        ApplicationManager.getApplication().executeOnPooledThread {
          currentThreadCoroutineScope().launch {
            assertEquals(id, coroutineContext[TestElementKey]?.value)
          }
        }
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `spawned coroutines are awaited`(): Unit = timeoutRunBlocking {
    val int = AtomicInteger(0)
    val semaphore = Semaphore(1)
    blockingContext {
      assertEquals(0, int.getAndIncrement())
      currentThreadCoroutineScope().launch {
        semaphore.timeoutWaitUp()
        assertEquals(2, int.getAndIncrement())
      }
      assertEquals(1, int.getAndIncrement())
      semaphore.up()
    }
    assertEquals(3, int.get())
  }

  @RepeatedTest(REPETITIONS)
  fun `EDT is not blocked, but launches are executed strictly later`(): Unit = timeoutRunBlocking {
    val int = AtomicInteger(0)
    withContext(Dispatchers.EDT) {
      blockingContext {
        currentThreadCoroutineScope().launch(Dispatchers.EDT) {
          assertEquals(1, int.getAndIncrement())
        }
        assertEquals(0, int.getAndIncrement())
      }
      assertEquals(2, int.getAndIncrement())
    }
    assertEquals(3, int.get())
  }

  // The users should use explicit scoping directives in deep coroutines
  @Test
  fun `no leaking BlockingJob`(): Unit = timeoutRunBlocking {
    val blockingContextScopeEnded = Semaphore(1)
    val innerExecuteOnPooledThreadEnded = Semaphore(1)
    blockingContextScope {
      ApplicationManager.getApplication().executeOnPooledThread {
        currentThreadCoroutineScope().launch {
          blockingContext {
            ApplicationManager.getApplication().executeOnPooledThread {
              assertNull(Cancellation.currentJob())
              blockingContextScopeEnded.timeoutWaitUp()
              // happens strictly outside `blockingContextScope`
              innerExecuteOnPooledThreadEnded.up()
            }
          }
        }
      }
    }
    blockingContextScopeEnded.up()
    innerExecuteOnPooledThreadEnded.timeoutWaitUp()
  }

  @Test
  fun `thread scope does not retain dispatcher`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      blockingContext {
        currentThreadCoroutineScope().launch {
          ThreadingAssertions.assertBackgroundThread()
        }
      }
    }
  }
}