// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlinx.coroutines.sync.Semaphore as KSemaphore

private const val REPETITIONS: Int = 100

abstract class SuspendingReadActionTest : CancellableReadActionTests() {

  @RepeatedTest(REPETITIONS)
  fun context(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()

    fun assertEmptyContext() {
      assertNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()
    }

    fun assertReadActionWithCurrentJob() {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessAllowed()
    }

    fun assertReadActionWithoutCurrentJob() {
      assertNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessAllowed()
    }

    assertEmptyContext()

    val result = cra {
      assertReadActionWithCurrentJob()
      runBlockingCancellable {
        assertReadActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
        withContext(Dispatchers.Default) {
          assertEmptyContext()
        }
        assertReadActionWithoutCurrentJob()
      }
      assertReadActionWithCurrentJob()
      42
    }
    assertEquals(42, result)

    assertEmptyContext()
  }

  @RepeatedTest(REPETITIONS)
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        cra {
          testNoExceptions()
          this@launch.coroutineContext.job.cancel()
          testExceptions()
        }
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun rethrow(): Unit = timeoutRunBlocking {
    testRethrow(object : Throwable() {})
    testRethrow(CancellationException())
    testRethrow(ProcessCanceledException())
    testRethrow(CannotReadException())
  }

  private suspend inline fun <reified T : Throwable> testRethrow(t: T) {
    lateinit var readJob: Job
    val thrown = assertThrows<T> {
      cra {
        readJob = requireNotNull(Cancellation.currentJob())
        throw t
      }
    }
    val cause = thrown.cause
    if (cause != null) {
      assertSame(t, cause) // kotlin trace recovery via [cause]
    }
    else {
      assertSame(t, thrown)
    }
    assertTrue(readJob.isCompleted)
    assertTrue(readJob.isCancelled)
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionHonorsConstraints
   */
  @RepeatedTest(REPETITIONS)
  fun `read action honors constraints`(): Unit = timeoutRunBlocking {
    val scheduled = KSemaphore(1, 1)
    lateinit var constraintRunnable: Runnable
    var satisfied = false
    val constraint = object : ReadConstraint {
      override fun toString(): String = "dummy constraint"
      override fun isSatisfied(): Boolean = satisfied
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.release()
      }
    }
    launch {
      assertFalse(constraint.isSatisfied())
      cra(constraint) {
        assertTrue(constraint.isSatisfied())
      }
    }
    scheduled.acquire() // constraint was unsatisfied initially
    satisfied = true
    constraintRunnable.run() // retry with satisfied constraint
  }

  @RepeatedTest(REPETITIONS)
  fun `read action with unsatisfiable constraint is cancellable`(): Unit = timeoutRunBlocking {
    val scheduled = KSemaphore(1, 1)
    lateinit var constraintRunnable: Runnable
    val unsatisfiableConstraint = object : ReadConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isSatisfied(): Boolean = false
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.release()
      }
    }
    val job = launch {
      cra(unsatisfiableConstraint) {
        fail("must not be called")
      }
    }
    scheduled.acquire()
    job.cancelAndJoin()
    LeakHunter.checkLeak(constraintRunnable, Continuation::class.java)
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionWorksInsideReadAction
   */
  @RepeatedTest(REPETITIONS)
  fun `read action works if already obtained`(): Unit = timeoutRunBlocking {
    cra {
      runBlockingCancellable {
        assertEquals(42, cra {
          42
        })
      }
    }
  }

  protected abstract suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T
}

class NonBlocking : SuspendingReadActionTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadAction(*constraints, action = action)
  }

  @RepeatedTest(REPETITIONS)
  fun `current job`(): Unit = timeoutRunBlocking {
    val coroutineJob = coroutineContext.job
    readAction {
      val readLoopJob = coroutineJob.children.single()
      assertCurrentJobIsChildOf(readLoopJob)
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is cancelled by write but not restarted because finished`(): Unit = timeoutRunBlocking {
    var attempt = false
    readAction {
      assertFalse(attempt)
      attempt = true
      waitForPendingWrite().up()
      assertThrows<JobCanceledException> { // assert but not throw
        ProgressManager.checkCanceled()
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is cancelled by write and restarted`(): Unit = timeoutRunBlocking {
    var attempts = 0
    readAction {
      when (attempts++) {
        0 -> {
          waitForPendingWrite().up()
          throw assertThrows<JobCanceledException> {
            ProgressManager.checkCanceled()
          }
        }
        1 -> {
          assertDoesNotThrow {
            ProgressManager.checkCanceled()
          }
        }
        else -> {
          fail()
        }
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action with constraints is cancelled by write and restarted`(): Unit = timeoutRunBlocking {
    val constraintScheduled = KSemaphore(1, 1)
    lateinit var constraintRunnable: Runnable
    val constraint = object : ReadConstraint {
      var satisfied: Boolean = true
      override fun toString(): String = "dummy constraint"
      override fun isSatisfied(): Boolean = satisfied
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        constraintScheduled.release()
      }
    }
    val application = ApplicationManager.getApplication()
    val job = launch {
      var attempts = 0
      constrainedReadAction(constraint) {
        when (attempts++) {
          0 -> {
            val pendingWrite = beforeWrite()
            application.invokeLater {
              application.runWriteAction { // cancel attempt 0
                constraint.satisfied = false
              }
            }
            pendingWrite.timeoutWaitUp()
            throw assertThrows<JobCanceledException> {
              ProgressManager.checkCanceled()
            }
          }
          1 -> {
            assertDoesNotThrow {
              ProgressManager.checkCanceled()
            }
          }
          else -> {
            fail()
          }
        }
      }
    }
    constraintScheduled.acquire()
    assertFalse(job.isCompleted)
    assertFalse(job.isCancelled)
    application.invokeLater {
      application.runWriteAction {
        constraint.satisfied = true
        constraintRunnable.run() // reschedule
      }
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionIsCancellable
   */
  @RepeatedTest(REPETITIONS)
  fun `read action with concurrent write actions`(): Unit = timeoutRunBlocking {
    val limit = 10
    val attempts = AtomicInteger()
    launch {
      readAction {
        if (attempts.getAndIncrement() < limit) {
          while (true) {
            ProgressManager.checkCanceled() // wait to be cancelled by
          }
        }
      }
      assertEquals(limit + 1, attempts.get()) // cancel 10 times, then pass 1 last time
    }
    launch(Dispatchers.EDT) {
      while (attempts.get() <= limit) {
        runWriteAction {}
        yield()
      }
    }
  }
}

class NonBlockingUndispatched : SuspendingReadActionTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadActionUndispatched(*constraints, action = action)
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionFailsInsideReadActionWhenConstraintsAreNotSatisfied
   */
  @RepeatedTest(REPETITIONS)
  fun `read action with unsatisfiable constraint fails if already obtained`(): Unit = timeoutRunBlocking {
    val unsatisfiableConstraint = object : ReadConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isSatisfied(): Boolean = false
      override fun schedule(runnable: Runnable): Unit = fail("must not be called")
    }
    cra {
      runBlockingCancellable {
        assertThrows<IllegalStateException> {
          cra(unsatisfiableConstraint) {
            fail("must not be called")
          }
        }
      }
    }
  }
}

class Blocking : SuspendingReadActionTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadActionBlocking(*constraints, action = action)
  }

  @RepeatedTest(REPETITIONS)
  fun `current job`(): Unit = timeoutRunBlocking {
    val coroutineJob = coroutineContext.job
    readActionBlocking {
      val readLoopJob = coroutineJob.children.single()
      assertSame(readLoopJob, Cancellation.currentJob())
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `blocking read action is not cancelled by write`(): Unit = timeoutRunBlocking {
    var attempt = false
    readActionBlocking {
      assertFalse(attempt)
      attempt = true
      waitForPendingWrite().up()
      testNoExceptions()
    }
  }
}

private fun beforeWrite(): Semaphore {
  val beforeWrite = Semaphore(1)
  val listenerDisposable = Disposer.newDisposable()
  ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
    override fun beforeWriteActionStart(action: Any) {
      beforeWrite.up()
      Disposer.dispose(listenerDisposable)
    }
  }, listenerDisposable)
  return beforeWrite
}
