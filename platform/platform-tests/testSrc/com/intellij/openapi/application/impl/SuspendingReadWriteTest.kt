// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation

abstract class SuspendingReadWriteTest : LightPlatformTestCase() {

  fun `test read action`(): Unit = runBlocking(Dispatchers.Default) {
    val application = ApplicationManager.getApplication()
    application.assertReadAccessNotAllowed()
    val result = cra {
      application.assertReadAccessAllowed()
      42
    }
    assertEquals(42, result)
  }

  fun `test exception is thrown`(): Unit = runBlocking(Dispatchers.Default) {
    class MyException : Throwable()
    try {
      cra {
        throw MyException()
      }
      @Suppress("UNREACHABLE_CODE")
      fail("exception must be thrown")
    }
    catch (ignored: MyException) {
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionHonorsConstraints
   */
  fun `test read action honors constraints`(): Unit = runBlocking {
    val scheduled = Semaphore(1)
    lateinit var constraintRunnable: Runnable
    var satisfied = false
    val constraint = object : ContextConstraint {
      override fun toString(): String = "dummy constraint"
      override fun isCorrectContext(): Boolean = satisfied
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.up()
      }
    }
    val job = launch(Dispatchers.Default) {
      assertFalse(constraint.isCorrectContext())
      cra(ReadConstraints.unconstrained().withConstraint(constraint)) {
        assertTrue(constraint.isCorrectContext())
      }
    }
    scheduled.waitTimeout() // constraint was unsatisfied initially
    satisfied = true
    constraintRunnable.run() // retry with satisfied constraint
    job.waitTimeout()
  }

  fun `test read action is cancellable`(): Unit = runBlocking {
    val inRead = Semaphore(1)
    val job = launch(Dispatchers.Default) {
      cra {
        inRead.up()
        while (true) {
          ProgressManager.checkCanceled()
          Thread.sleep(10)
        }
      }
    }
    inRead.waitTimeout()
    job.cancelAndWaitTimeout()
  }

  fun `test suspending action inside read action is cancellable`(): Unit = runBlocking {
    val inRead = Semaphore(1)
    val cancelled = Semaphore(1)
    val job = launch(Dispatchers.Default) {
      cra {
        runBlockingCancellable {
          inRead.up()
          cancelled.waitTimeout()
          ensureActive() // should throw
          fail()
        }
      }
    }
    inRead.waitTimeout()
    job.cancel()
    cancelled.up()
    job.waitTimeout()
  }

  fun `test read action with unsatisfiable constraint is cancellable`(): Unit = runBlocking {
    val scheduled = Semaphore(1)
    lateinit var constraintRunnable: Runnable
    val unsatisfiableConstraint = object : ContextConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isCorrectContext(): Boolean = false
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.up()
      }
    }
    val job = launch(Dispatchers.Default) {
      cra(ReadConstraints.unconstrained().withConstraint(unsatisfiableConstraint)) {
        fail("must not be called")
      }
    }
    scheduled.waitTimeout()
    job.cancelAndWaitTimeout()
    LeakHunter.checkLeak(constraintRunnable, Continuation::class.java)
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionWorksInsideReadAction
   */
  fun `test read action works inside non-coroutine read action`(): Unit = runBlocking {
    launch(Dispatchers.Default) {
      runReadAction { // blocking variant
        runBlocking {
          assertEquals(42, cra { 42 })
        }
      }
    }.waitTimeout()
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionFailsInsideReadActionWhenConstraintsAreNotSatisfied
   */
  fun `test read action with unsatisfiable constraint fails inside non-coroutine read action`(): Unit = runBlocking(Dispatchers.Default) {
    val unsatisfiableConstraint = object : ContextConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isCorrectContext(): Boolean = false
      override fun schedule(runnable: Runnable) = fail("must not be called")
    }
    runReadAction {
      runBlocking {
        try {
          cra(ReadConstraints.unconstrained().withConstraint(unsatisfiableConstraint)) {
            fail("must not be called")
          }
          fail("exception must be thrown")
        }
        catch (ignored: IllegalStateException) {
        }
      }
    }
  }

  protected abstract suspend fun <T> cra(constraints: ReadConstraints = ReadConstraints.unconstrained(), action: () -> T): T
}

class NonBlocking : SuspendingReadWriteTest() {

  override suspend fun <T> cra(constraints: ReadConstraints, action: () -> T): T {
    return constrainedReadAction(constraints, action)
  }

  fun `test read action is cancelled by write but not restarted because finished`(): Unit = runBlocking {
    val inRead = Semaphore(1)
    val beforeWrite = beforeWrite()
    val job = launch(Dispatchers.Default) {
      var attempt = false
      readAction {
        assertFalse(attempt)
        attempt = true
        inRead.up()
        beforeWrite.waitTimeout()
        assertTrue(Cancellation.isCancelled())
      }
    }
    inRead.waitTimeout()
    runWriteAction {}
    job.waitTimeout()
  }

  fun `test read action is cancelled by write and restarted`(): Unit = runBlocking {
    val job = twoAttemptJob(this, ReadConstraints.unconstrained())
    runWriteAction {}
    withTimeout(1000) {
      while (job.isActive) {
        coroutineContext.ensureActive()
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  fun `test read action with constraints is cancelled by write and restarted`(): Unit = runBlocking {
    val job = twoAttemptJob(this, ReadConstraints.inSmartMode(project))
    val application = ApplicationManager.getApplication()
    val dumbService = DumbServiceImpl.getInstance(project)
    application.runWriteAction { // cancel attempt 0
      dumbService.isDumb = true
    }
    UIUtil.dispatchAllInvocationEvents() // retry with unsatisfied constraint
    application.runWriteAction {
      dumbService.isDumb = false
    }
    UIUtil.dispatchAllInvocationEvents() // retry with satisfied constraint, attempt 1
    job.waitTimeout()
  }

  private fun twoAttemptJob(cs: CoroutineScope, constraints: ReadConstraints): Job {
    val inRead = Semaphore(1)
    val beforeWrite = beforeWrite()
    val job = cs.launch(Dispatchers.Default) {
      var attempts = 0
      constrainedReadAction(constraints) {
        inRead.up()
        beforeWrite.waitTimeout()
        when (attempts) {
          0 -> assertTrue(Cancellation.isCancelled())
          1 -> assertFalse(Cancellation.isCancelled())
          else -> fail()
        }
        attempts++
        ProgressManager.checkCanceled()
      }
    }
    inRead.waitTimeout()
    return job
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionIsCancellable
   */
  fun `test read action with concurrent write actions`(): Unit = runBlocking {
    val limit = 10
    val attempts = AtomicInteger()
    val job = launch(Dispatchers.Default) {
      readAction {
        if (attempts.getAndIncrement() < limit) {
          while (true) {
            ProgressManager.checkCanceled() // wait to be cancelled by
          }
        }
      }
      assertEquals(limit + 1, attempts.get()) // cancel 10 times, then pass 1 last time
    }
    while (attempts.get() <= limit) {
      runWriteAction {}
      UIUtil.dispatchAllInvocationEvents()
      delay(10)
    }
    job.waitTimeout()
  }
}

class Blocking : SuspendingReadWriteTest() {

  override suspend fun <T> cra(constraints: ReadConstraints, action: () -> T): T {
    return constrainedReadActionBlocking(constraints, action)
  }

  fun `test blocking read action is not cancelled by write`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()
    val inRead = Semaphore(1)
    val beforeWrite = beforeWrite()
    val job = launch(Dispatchers.Default) {
      var attempt = false
      cra {
        assertFalse(attempt)
        attempt = true
        inRead.up()
        beforeWrite.waitTimeout()
        assertFalse(Cancellation.isCancelled())
        ProgressManager.checkCanceled()
      }
    }
    inRead.waitFor()
    application.runWriteAction {}
    job.waitTimeout()
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

private fun Semaphore.waitTimeout() {
  TestCase.assertTrue(waitFor(1000))
}

private suspend fun Job.waitTimeout() {
  withTimeout(1000) {
    join()
  }
}

private suspend fun Job.cancelAndWaitTimeout() {
  withTimeout(1000) {
    cancelAndJoin()
  }
}
