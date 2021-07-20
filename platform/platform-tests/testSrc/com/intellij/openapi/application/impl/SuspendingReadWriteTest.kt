// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.progress.Progress
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

class SuspendingReadWriteTest : LightPlatformTestCase() {

  fun `test read action`(): Unit = runBlocking(Dispatchers.Default) {
    val application = ApplicationManager.getApplication()
    application.assertReadAccessNotAllowed()
    val result = readAction {
      application.assertReadAccessAllowed()
      42
    }
    assertEquals(42, result)
  }

  fun `test exception is thrown`(): Unit = runBlocking(Dispatchers.Default) {
    class MyException : Throwable()
    try {
      readAction {
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
      constrainedReadAction(ReadConstraints.unconstrained().withConstraint(constraint)) {
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
      readAction { progress ->
        inRead.up()
        while (true) {
          progress.checkCancelled()
          Thread.sleep(10)
        }
      }
    }
    inRead.waitTimeout()
    job.cancelAndWaitTimeout()
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
      constrainedReadAction(ReadConstraints.unconstrained().withConstraint(unsatisfiableConstraint)) {
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
          assertEquals(42, readAction { 42 })
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
          constrainedReadAction(ReadConstraints.unconstrained().withConstraint(unsatisfiableConstraint)) {
            fail("must not be called")
          }
          fail("exception must be thrown")
        }
        catch (ignored: IllegalStateException) {
        }
      }
    }
  }

  fun `test read action is cancelled by write but not restarted because finished`(): Unit = runBlocking {
    val inRead = Semaphore(1)
    val beforeWrite = beforeWrite()
    val job = launch(Dispatchers.Default) {
      var attempt = false
      readAction { progress ->
        assertFalse(attempt)
        attempt = true
        inRead.up()
        beforeWrite.waitTimeout()
        assertTrue(progress.isCancelled)
      }
    }
    inRead.waitTimeout()
    runWriteAction {}
    job.waitTimeout()
  }

  fun `test read action is cancelled by write and restarted`(): Unit = runBlocking {
    val job = twoAttemptJob(this, ReadConstraints.unconstrained())
    runWriteAction {}
    UIUtil.dispatchAllInvocationEvents()
    job.waitTimeout()
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
      constrainedReadAction(constraints) { progress: Progress ->
        inRead.up()
        beforeWrite.waitTimeout()
        when (attempts) {
          0 -> assertTrue(progress.isCancelled)
          1 -> assertFalse(progress.isCancelled)
          else -> fail()
        }
        attempts++
        progress.checkCancelled()
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
      readAction { progress ->
        if (attempts.getAndIncrement() < limit) {
          while (true) {
            progress.checkCancelled() // wait to be cancelled by
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
