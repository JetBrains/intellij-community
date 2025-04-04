// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.*
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

private const val REPETITIONS = 100

@TestApplication
@ExtendWith(ThreadContextPropagationTest.Enabler::class)
class CurrentThreadCoroutineScopeTest {

  @Test
  fun `capturing of thread context`(): Unit = timeoutRunBlocking {
    val semaphore = Semaphore(1)
    val id = "abcde"
    withContext(TestElement(id)) {
      blockingContextScope {
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
      assertErrorLogged<IllegalStateException> {
        currentThreadCoroutineScope()
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
    blockingContextScope {
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
      blockingContextScope {
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
      blockingContextScope {
        currentThreadCoroutineScope().launch {
          ThreadingAssertions.assertBackgroundThread()
        }
      }
    }
  }

  class E1 : AbstractCoroutineContextElement(Key), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<E1>
  }

  class E2 : AbstractCoroutineContextElement(Key), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<E2>
  }

  class E3 : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<E3>
  }


  @Test
  fun `blockingContextScope retains only those elements that were present at the moment of invocation`(): Unit = timeoutRunBlocking {
    withContext(E2() + E3()) {
      blockingContextScope {
        installThreadContext(currentThreadContext() + E1(), true).use {
          application.executeOnPooledThread {
            val context = currentThreadContext()
            assertNull(context[E1])
            assertNotNull(context[E2])
            assertNotNull(context[E3])
          }
        }
      }
    }
  }

  @Test
  fun `fixCurrentThreadScope captures the context of its definition`(): Unit = timeoutRunBlocking {
    withContext(E1()) {
      val (_, job) = withCurrentThreadCoroutineScopeBlocking {
        installThreadContext(currentThreadContext() + E2(), true).use {
          currentThreadCoroutineScope().launch {
            val context = currentThreadContext()
            assertNotNull(context[E1])
            assertNull(context[E2])
          }
        }
      }
      job.join()
    }
  }

  @Test
  fun `fixCurrentThreadScope completes job when all children are completed`(): Unit = timeoutRunBlocking {
    val launchCanFinish = Job()
    val launch2CanFinish = Job()
    val (_, job) = withCurrentThreadCoroutineScopeBlocking {
      currentThreadCoroutineScope().launch {
        launchCanFinish.join()
        launch {
          launch2CanFinish.join()
        }
      }
    }
    assertTrue(job.isActive)
    launchCanFinish.complete()
    delay(100)
    assertTrue(job.isActive)
    launch2CanFinish.complete()
    job.join()
  }

  @Test
  fun `fixCurrentThreadScope should propagate parent job cancellation`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val parentJob = Job(coroutineContext.job)

    val latch = Job(coroutineContext.job)
    val wasCancelled = AtomicBoolean(false)

    installThreadContext(currentThreadContext() + parentJob).use {
      val (_, job) = withCurrentThreadCoroutineScopeBlocking {
        currentThreadCoroutineScope().launch {
          try {
            suspendCancellableCoroutine { }
          }
          catch (e: CancellationException) {
            wasCancelled.set(true)
            latch.complete()
          }
        }
      }

      // Cancel the parent job
      parentJob.cancel()

      // Wait for cancellation
      latch.asCompletableFuture().get()

      assertTrue(wasCancelled.get(), "Child coroutine should be cancelled")
      assertTrue(job.isCancelled, "The job should be cancelled")
    }
  }

  @Test
  fun `fixCurrentThreadScope should handle nested coroutine scopes`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val nestedCompletion = AtomicBoolean(false)

    val (_, outerJob) = withCurrentThreadCoroutineScopeBlocking {

      val (_, nestedJob) = withCurrentThreadCoroutineScopeBlocking {
        currentThreadCoroutineScope().launch {
          delay(100)
          nestedCompletion.set(true)
        }
      }

      nestedJob.asCompletableFuture().join()
      assertTrue(nestedCompletion.get(), "The coroutine in nested scope must be completed at this point")
    }

    outerJob.join()
  }

  @Test
  fun `fixCurrentThreadScope should not handle ApplicationManager executeOnPooledThread`() = timeoutRunBlocking(context = Dispatchers.Default) {

    val pooledThreadExecuted = Job()

    runBlocking {
      val (_, job) = withCurrentThreadCoroutineScopeBlocking {
        application.executeOnPooledThread {
          Thread.sleep(100)
          pooledThreadExecuted.complete()
        }
      }

      // This shouldn't wait for the pooled thread to complete
      delay(50.milliseconds)
      assertFalse(job.isActive, "The job should be completed")
      assertFalse(pooledThreadExecuted.isCompleted, "The pooled thread should not have completed yet")
      pooledThreadExecuted.join()
    }
  }

  @Test
  fun `currentThreadCoroutineScope is available in modal progresses`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    blockingContextScope {
      val innerCoroutineCompleted = AtomicBoolean(false)
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        @Suppress("ForbiddenInSuspectContextMethod")
        currentThreadCoroutineScope().launch {
          delay(100)
          innerCoroutineCompleted.set(true)
        }
      }
      assertTrue(innerCoroutineCompleted.get(), "The coroutine in modal dialog must be completed at this point")
    }
  }

  @Test
  fun `currentThreadCoroutineScope propagates exceptions`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    blockingContextScope {
      assertThrows<IllegalStateException> {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          @Suppress("ForbiddenInSuspectContextMethod")
          currentThreadCoroutineScope().launch {
            delay(100)
            throw IllegalStateException("Something went intentionally wrong")
          }
        }
      }
    }
  }
}