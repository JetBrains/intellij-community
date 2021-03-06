// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerImpl
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertThrows
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation

class SuspendingReadWriteTest : LightPlatformTestCase() {

  private inline fun <reified T : Throwable> assertThrows(noinline action: () -> Unit) {
    assertThrows(T::class.java, action)
  }

  private suspend fun Job.join(timeoutMs: Long) {
    withTimeout(timeoutMs) {
      join()
    }
  }

  private fun setupUncommittedDocument() {
    (PsiDocumentManager.getInstance(project) as PsiDocumentManagerImpl).disableBackgroundCommit(testRootDisposable)
    val file = createFile("a.txt", "")
    WriteCommandAction.runWriteCommandAction(project) {
      file.viewProvider.document!!.insertString(0, "a")
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionWorksInsideReadAction
   */
  fun `test suspending read action works inside read action`(): Unit = runBlocking {
    launch(Dispatchers.Default) {
      runReadAction { // blocking variant
        runBlocking {
          assertEquals(42, readAction { 42 })
        }
      }
    }.join(1000)
  }

  fun `test cancel long read action job`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()
    val lock = Semaphore(1)
    val job = launch(Dispatchers.Default) {
      check(!application.isReadAccessAllowed)
      readAction { ctx ->
        check(application.isReadAccessAllowed)
        lock.up()
        while (true) {
          ctx.ensureActive()
          Thread.sleep(100)
        }
      }
    }
    lock.waitFor()
    withTimeout(2000) {
      job.cancelAndJoin()
    }
  }

  fun `test cancel long read action indicator`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()
    val lock = Semaphore(1)
    val job = launch(Dispatchers.Default) {
      check(!application.isReadAccessAllowed)
      readAction {
        runUnderIndicator {
          check(application.isReadAccessAllowed)
          lock.up()
          while (true) {
            ProgressManager.checkCanceled()
            Thread.sleep(100)
          }
        }
      }
    }
    lock.waitFor()
    withTimeout(2000) {
      job.cancelAndJoin()
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionIsCancellable
   */
  fun `test read action is cancelled on write`(): Unit = runBlocking {
    val limit = 10
    val attemptCount = AtomicInteger()
    val job = launch(Dispatchers.Default) {
      val result = readAction { ctx ->
        if (attemptCount.incrementAndGet() < limit) {
          while (true) {
            ctx.ensureActive()
          }
        }
        "a"
      }
      assertEquals("a", result)
      assertTrue(attemptCount.toString(), attemptCount.get() >= limit)
    }
    while (attemptCount.get() < limit) {
      runWriteAction {}
      UIUtil.dispatchAllInvocationEvents()
    }
    job.join(1000)
  }

  fun `test write action after read is almost finished`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()

    val inRead = Semaphore(1)
    val beforeWrite = Semaphore(1)

    val job: Deferred<Int> = async(Dispatchers.Default) {
      var counter = 0
      readAction {
        counter++
        inRead.up()
        beforeWrite.waitFor()
        counter
      }
    }

    inRead.waitFor()

    val listenerDisposable = Disposer.newDisposable()
    application.addApplicationListener(object : ApplicationListener {
      override fun beforeWriteActionStart(action: Any) {
        beforeWrite.up() // executed after listener cancels readJob
      }
    }, listenerDisposable)
    try {
      application.runWriteAction {}
    }
    finally {
      Disposer.dispose(listenerDisposable)
    }

    withTimeout(2000) {
      while (job.isActive) {
        UIUtil.dispatchAllInvocationEvents()
        delay(10)
      }
      // read action block finishes fully, but the read job is cancelled, so it's restarted again
      assertEquals(2, job.await())
    }
  }

  fun `test write action while read is running`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()
    val inRead = Semaphore(1)
    val inWrite = Semaphore(1)
    val job: Deferred<Int> = async(Dispatchers.Default) {
      check(!application.isReadAccessAllowed)
      var counter = 0
      readAction { ctx ->
        check(application.isReadAccessAllowed)
        ctx.ensureActive()
        counter++
        inRead.up()
        while (!inWrite.waitFor(100)) {
          ctx.ensureActive()
        }
        counter
      }
    }
    inRead.waitFor()
    application.runWriteAction {
      inWrite.up()
    }
    withTimeout(2000) {
      while (job.isActive) {
        UIUtil.dispatchAllInvocationEvents()
        delay(10)
      }
      assertEquals(2, job.await())
    }
  }

  fun `test suspend and restart smart read action`(): Unit = runBlocking {
    val application = ApplicationManager.getApplication()
    val dumbService = DumbServiceImpl.getInstance(project)

    val inRead = Semaphore(1)
    val inWrite = Semaphore(1)

    val job: Deferred<Int> = async(Dispatchers.Default) {
      check(!application.isReadAccessAllowed)
      check(!dumbService.isDumb)
      var counter = 0
      smartReadAction(project) { ctx ->
        check(application.isReadAccessAllowed)
        check(!dumbService.isDumb)
        ctx.ensureActive()
        counter++
        inRead.up()
        while (!inWrite.waitFor(100)) {
          ctx.ensureActive()
        }
        counter
      }
    }
    inRead.waitFor()

    application.runWriteAction {
      dumbService.isDumb = true
      inWrite.up()
    }
    UIUtil.dispatchAllInvocationEvents()
    application.runWriteAction {
      dumbService.isDumb = false
    }
    UIUtil.dispatchAllInvocationEvents()

    withTimeout(2000) {
      while (job.isActive) {
        UIUtil.dispatchAllInvocationEvents()
        delay(10)
      }
      assertEquals(2, job.await())
    }
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionHonorsConstraints
   */
  fun `test read action honors constraints`(): Unit = runBlocking {
    setupUncommittedDocument()
    val started = AtomicBoolean()
    val job = launch(Dispatchers.Default) {
      val result = constrainedReadAction(ReadConstraints.withDocumentsCommitted(project)) {
        started.set(true)
        42
      }
      assertEquals(42, result)
    }
    assertFalse(started.get())
    UIUtil.dispatchAllInvocationEvents()
    assertFalse(started.get())
    UIUtil.dispatchAllInvocationEvents()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    UIUtil.dispatchAllInvocationEvents()
    job.join(1000)
    assertTrue(started.get())
  }

  /**
   * @see NonBlockingReadActionTest.testSyncExecutionFailsInsideReadActionWhenConstraintsAreNotSatisfied
   */
  fun `test execution fails inside read action when constraints are not satisfied`(): Unit = runBlocking {
    setupUncommittedDocument()
    val job = launch(Dispatchers.Default) {
      ReadAction.run<RuntimeException> {
        assertThrows<IllegalStateException> {
          runBlocking {
            constrainedReadAction(ReadConstraints.withDocumentsCommitted(project)) {}
          }
        }
      }
    }
    job.join(1000)
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
        fail("must never be run")
      }
    }
    scheduled.waitFor()
    job.cancelAndJoin()
    LeakHunter.checkLeak(constraintRunnable, Continuation::class.java)
  }
}
