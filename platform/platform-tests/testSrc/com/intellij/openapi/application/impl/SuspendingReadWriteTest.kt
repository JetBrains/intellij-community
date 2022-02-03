// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
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
    val constraint = object : ReadConstraint {
      override fun toString(): String = "dummy constraint"
      override fun isSatisfied(): Boolean = satisfied
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.up()
      }
    }
    val job = launch(Dispatchers.Default) {
      assertFalse(constraint.isSatisfied())
      cra(constraint) {
        assertTrue(constraint.isSatisfied())
      }
    }
    scheduled.timeoutWaitUp() // constraint was unsatisfied initially
    satisfied = true
    constraintRunnable.run() // retry with satisfied constraint
    job.timeoutJoin()
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
    inRead.timeoutWaitUp()
    job.cancel()
    job.timeoutJoin()
    job.job.cancel()
    job.job.timeoutJoin()
  }

  fun `test suspending action inside read action is cancellable`(): Unit = runBlocking {
    val inRead = Semaphore(1)
    val cancelled = Semaphore(1)
    val job = launch(Dispatchers.Default) {
      cra {
        runBlockingCancellable {
          inRead.up()
          cancelled.timeoutWaitUp()
          ensureActive() // should throw
          fail()
        }
      }
    }
    inRead.timeoutWaitUp()
    job.cancel()
    cancelled.up()
    job.timeoutJoin()
  }

  fun `test read action with unsatisfiable constraint is cancellable`(): Unit = runBlocking {
    val scheduled = Semaphore(1)
    lateinit var constraintRunnable: Runnable
    val unsatisfiableConstraint = object : ReadConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isSatisfied(): Boolean = false
      override fun schedule(runnable: Runnable) {
        constraintRunnable = runnable
        scheduled.up()
      }
    }
    val job = launch(Dispatchers.Default) {
      cra(unsatisfiableConstraint) {
        fail("must not be called")
      }
    }
    scheduled.timeoutWaitUp()
    job.job.cancel()
    job.job.timeoutJoin()
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
    }.timeoutJoin()
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionFailsInsideReadActionWhenConstraintsAreNotSatisfied
   */
  fun `test read action with unsatisfiable constraint fails inside non-coroutine read action`(): Unit = runBlocking(Dispatchers.Default) {
    val unsatisfiableConstraint = object : ReadConstraint {
      override fun toString(): String = "unsatisfiable constraint"
      override fun isSatisfied(): Boolean = false
      override fun schedule(runnable: Runnable) = fail("must not be called")
    }
    runReadAction {
      runBlocking {
        try {
          cra(unsatisfiableConstraint) {
            fail("must not be called")
          }
          fail("exception must be thrown")
        }
        catch (ignored: IllegalStateException) {
        }
      }
    }
  }

  protected abstract suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T
}

class NonBlocking : SuspendingReadWriteTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadAction(*constraints, action = action)
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
        beforeWrite.timeoutWaitUp()
        assertTrue(Cancellation.isCancelled())
      }
    }
    inRead.timeoutWaitUp()
    runWriteAction {}
    job.timeoutJoin()
  }

  fun `test read action is cancelled by write and restarted`(): Unit = runBlocking {
    val job = twoAttemptJob(this)
    runWriteAction {}
    withTimeout(TEST_TIMEOUT_MS) {
      while (job.isActive) {
        coroutineContext.ensureActive()
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  fun `test read action with constraints is cancelled by write and restarted`(): Unit = runBlocking {
    val job = twoAttemptJob(this, ReadConstraint.inSmartMode(project))
    val application = ApplicationManager.getApplication()
    val dumbService = DumbServiceImpl.getInstance(project)
    application.runWriteAction { // cancel attempt 0
      dumbService.isDumb = true
    }
    UIUtil.dispatchAllInvocationEvents() // retry with unsatisfied constraint
    application.runWriteAction {
      dumbService.isDumb = false
    }
    // retry with unsatisfied constraint
    withTimeout(TEST_TIMEOUT_MS) {
      while (job.isActive && isActive) {
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  private fun twoAttemptJob(cs: CoroutineScope, vararg constraints: ReadConstraint): Job {
    val inRead = Semaphore(1)
    val beforeWrite = beforeWrite()
    val job = cs.launch(Dispatchers.Default) {
      var attempts = 0
      constrainedReadAction(*constraints) {
        inRead.up()
        beforeWrite.timeoutWaitUp()
        when (attempts) {
          0 -> assertTrue(Cancellation.isCancelled())
          1 -> assertFalse(Cancellation.isCancelled())
          else -> fail()
        }
        attempts++
        ProgressManager.checkCanceled()
      }
    }
    inRead.timeoutWaitUp()
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
    job.timeoutJoin()
  }
}

class Blocking : SuspendingReadWriteTest() {

  override suspend fun <T> cra(vararg constraints: ReadConstraint, action: () -> T): T {
    return constrainedReadActionBlocking(*constraints, action = action)
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
        beforeWrite.timeoutWaitUp()
        assertFalse(Cancellation.isCancelled())
        ProgressManager.checkCanceled()
      }
    }
    inRead.waitFor()
    application.runWriteAction {}
    job.timeoutJoin()
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
