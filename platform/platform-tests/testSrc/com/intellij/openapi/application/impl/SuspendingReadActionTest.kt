// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.ImplicitBlockingContextTest
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.runWithImplicitBlockingContextEnabled
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Semaphore as KSemaphore

private const val REPETITIONS: Int = 100

@ExtendWith(ImplicitBlockingContextTest.Enabler::class)
abstract class SuspendingReadActionTest : CancellableReadActionTests() {

  @RepeatedTest(REPETITIONS)
  fun context(): Unit = runWithImplicitBlockingContextEnabled {
    timeoutRunBlocking {
      val rootJob = coroutineContext.job
      val application = ApplicationManager.getApplication()

      fun assertEmptyContext(job: Job) {
        assertEquals(job, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessNotAllowed()
      }

      fun assertNestedContext(job: Job) {
        assertEquals(job, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
      }

      fun assertReadActionWithCurrentJob() {
        assertNotNull(Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
      }

      fun assertReadActionWithoutCurrentJob(job: Job) {
        assertEquals(job, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
      }

      assertEmptyContext(rootJob)

      val result = cra {
        assertReadActionWithCurrentJob()
        runBlockingCancellable {
          val suspendingJob = Cancellation.currentJob()!!
          assertReadActionWithoutCurrentJob(suspendingJob) // TODO consider explicitly turning off RA inside runBlockingCancellable
          withContext(Dispatchers.Default) {
            if (isLockStoredInContext) {
              assertNestedContext(coroutineContext.job)
            }
            else {
              assertEmptyContext(coroutineContext.job)
            }
          }
          assertReadActionWithoutCurrentJob(suspendingJob)
        }
        assertReadActionWithCurrentJob()
        42
      }
      assertEquals(42, result)

      assertEmptyContext(rootJob)
    }
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
    testRwRethrow {
      cra(action = it)
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionHonorsConstraints
   */
  @RepeatedTest(REPETITIONS)
  fun `read action honors constraints`(): Unit = timeoutRunBlocking {
    val scheduled = KSemaphore(1, 1)
    lateinit var constraintContinuation: Continuation<Unit>
    var satisfied = false
    val constraint = object : ReadConstraint {
      override fun toString(): String = "dummy constraint"
      override fun isSatisfied(): Boolean = satisfied
      override suspend fun awaitConstraint() {
        suspendCancellableCoroutine {
          constraintContinuation = it
          scheduled.release()
        }
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
    constraintContinuation.resume(Unit) // retry with satisfied constraint
  }

  @RepeatedTest(REPETITIONS)
  fun `read action with unsatisfiable constraint is cancellable`(): Unit = timeoutRunBlocking {
    val scheduled = KSemaphore(1, 1)
    val unsatisfiableConstraint = object : ReadConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isSatisfied(): Boolean = false
      override suspend fun awaitConstraint() {
        suspendCancellableCoroutine<Unit> {
          scheduled.release()
        }
      }
    }
    val job = launch {
      cra(unsatisfiableConstraint) {
        fail("must not be called")
      }
    }
    scheduled.acquire()
    job.cancelAndJoin()
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

class NonBlockingSuspendingReadActionTest : SuspendingReadActionTest() {

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
      assertThrows<CannotReadException> { // assert but not throw
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
          throw assertThrows<CannotReadException> {
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
  fun `read action is cancelled by write and restarted with nested runBlockingCancellable`(): Unit = timeoutRunBlocking {
    var attempts = 0
    val result = readAction {
      when (attempts++) {
        0 -> {
          throw assertThrows<CannotReadException> {
            runBlockingCancellable {
              throw assertThrows<CannotReadException> {
                blockingContext {
                  throw assertThrows<CannotReadException> {
                    runBlockingCancellable {
                      throw assertThrows<CannotReadException> {
                        blockingContext {
                          throw assertThrows<CannotReadException> {
                            runBlockingCancellable {
                              waitForPendingWrite().up()
                              awaitCancellation()
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        1 -> {
          assertDoesNotThrow {
            ProgressManager.checkCanceled()
          }
          42
        }
        else -> {
          fail()
        }
      }
    }
    assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is cancelled by write and restarted with blockingContextToIndicator`(): Unit = timeoutRunBlocking {
    var attempts = 0
    val result = readAction {
      when (attempts++) {
        0 -> {
          throw assertThrows<CannotReadException> {
            blockingContextToIndicator {
              waitForPendingWrite().up()
              throw assertThrows<CannotReadException> {
                ProgressManager.checkCanceled()
              }
            }
          }
        }
        1 -> {
          assertDoesNotThrow {
            ProgressManager.checkCanceled()
          }
          42
        }
        else -> {
          fail()
        }
      }
    }
    assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `read action with constraints is cancelled by write and restarted`(): Unit = timeoutRunBlocking {
    val constraintScheduled = KSemaphore(1, 1)
    lateinit var constraintContinuation: Continuation<Unit>
    val constraint = object : ReadConstraint {
      var satisfied: Boolean = true
      override fun toString(): String = "dummy constraint"
      override fun isSatisfied(): Boolean = satisfied
      override suspend fun awaitConstraint() {
        suspendCancellableCoroutine {
          constraintContinuation = it
          constraintScheduled.release()
        }
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
            throw assertThrows<CannotReadException> {
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
        constraintContinuation.resume(Unit) // reschedule
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

class NonBlockingUndispatchedSuspendingReadActionTest : SuspendingReadActionTest() {

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
      override suspend fun awaitConstraint(): Unit = fail("must not be called")
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

class BlockingSuspendingReadActionTest : SuspendingReadActionTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadActionBlocking(*constraints, action = action)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @RepeatedTest(REPETITIONS)
  fun `current job`(): Unit = timeoutRunBlocking {
    val coroutineJob = coroutineContext.job
    readActionBlocking {
      assertSame(coroutineJob, Cancellation.currentJob()?.parent?.parent)
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
