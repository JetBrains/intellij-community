// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.testExceptions
import com.intellij.openapi.progress.testNoExceptions
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.use
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.application
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val repetitions: Int = 100

@TestApplication
class SuspendingWriteActionTest {

  @RepeatedTest(repetitions)
  fun context() {
    timeoutRunBlocking {
      val application = ApplicationManager.getApplication()
      val rootJob = coroutineContext.job

      fun assertEmptyContext(job: Job) {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertEquals(job, Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
      }

      fun assertWriteActionWithCurrentJob() {
        Assertions.assertTrue(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertWriteAccessAllowed()
      }

      fun assertNoWriteActionWithoutCurrentJob(job: Job) {
        Assertions.assertTrue(EDT.isCurrentThreadEdt())
        Assertions.assertEquals(job, Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertTrue(application.isWriteAccessAllowed)
      }

      assertEmptyContext(rootJob)

      val result = edtWriteAction {
        assertWriteActionWithCurrentJob()
        runBlockingCancellable {
          val writeJob = coroutineContext.job
          assertNoWriteActionWithoutCurrentJob(writeJob) // TODO consider explicitly turning off RA inside runBlockingCancellable
          withContext(Dispatchers.Default) {
            assertEmptyContext(coroutineContext.job)
          }
          assertNoWriteActionWithoutCurrentJob(writeJob)
        }
        assertWriteActionWithCurrentJob()
        42
      }
      Assertions.assertEquals(42, result)

      assertEmptyContext(rootJob)
    }
  }

  @RepeatedTest(repetitions)
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        edtWriteAction {
          testNoExceptions()
          this.coroutineContext.job.cancel()
          testExceptions()
        }
      }
    }
  }

  @RepeatedTest(repetitions)
  fun rethrow(): Unit = timeoutRunBlocking {
    testRwRethrow {
      edtWriteAction(it)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `current job`(): Unit = timeoutRunBlocking {
    val coroutineJob = coroutineContext.job
    edtWriteAction {
      Assertions.assertSame(coroutineJob, Cancellation.currentJob()?.parent)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testWriteActionListenerMustReceiveCorrectClazz() {
    Disposer.newDisposable().use { disposable ->
      val listener = object : WriteActionListener {
        val expectedClass: AtomicReference<Class<*>?> = AtomicReference<Class<*>?>()
        val calledLog: StringBuffer = StringBuffer()
        override fun beforeWriteActionStart(action: Class<*>) {
          Assertions.assertSame(expectedClass.get(), action)
          calledLog.append("beforeWriteActionStart;")
        }

        override fun writeActionStarted(action: Class<*>) {
          Assertions.assertSame(expectedClass.get(), action)
          calledLog.append("writeActionStarted;")
        }

        override fun writeActionFinished(action: Class<*>) {
          Assertions.assertSame(expectedClass.get(), action)
          calledLog.append("writeActionFinished;")
        }

        override fun afterWriteActionFinished(action: Class<*>) {
          Assertions.assertSame(expectedClass.get(), action)
          calledLog.append("afterWriteActionFinished;")
        }

        fun assertCorrectClassPassed(expectedObject: Any, writeAction: ()->Unit) {
          calledLog.setLength(0)
          expectedClass.set(expectedObject.javaClass)
          writeAction.invoke()
          Assertions.assertEquals("beforeWriteActionStart;writeActionStarted;writeActionFinished;afterWriteActionFinished;",
                                  calledLog.toString())
        }
      }
      val application = ApplicationManagerEx.getApplicationEx()
      application.addWriteActionListener(listener, disposable)

      val action: Runnable = {}
      listener.assertCorrectClassPassed(action) {
        application.runWriteAction(action)
      }

      val computation: Computable<String> = { "" }
      listener.assertCorrectClassPassed(computation) {
        application.runWriteAction(computation)
      }

      val t: ThrowableComputable<String, Throwable> = { "" }
      listener.assertCorrectClassPassed(t) { application.runWriteAction(t) }

      val writeLambda: () -> Unit = {  }
      listener.assertCorrectClassPassed(writeLambda) {
        runBlocking {
          edtWriteAction(writeLambda)
        }
      }

      listener.assertCorrectClassPassed(writeLambda) {
        runWriteAction(writeLambda)
      }
      listener.assertCorrectClassPassed(writeLambda) {
        runBlocking {
          edtWriteAction(writeLambda)
        }
      }
      listener.assertCorrectClassPassed(writeLambda) {
        runBlocking {
          runUndoTransparentWriteAction(writeLambda)
        }
      }

      val tr: ThrowableRunnable<RuntimeException> = {}
      listener.assertCorrectClassPassed(tr) {
        com.intellij.openapi.application.WriteAction.run(tr)
      }

      listener.assertCorrectClassPassed(tr) {
        com.intellij.openapi.application.WriteAction.runAndWait(tr)
      }
    }
  }

  @Test
  fun `pending read actions are canceled on reacquisition of write lock`(): Unit = concurrencyTest {
    readAction {  } // init internal structures
    val wasCanceled = AtomicBoolean(false)
    backgroundWriteAction {
      ApplicationManagerEx.getApplicationEx().threadingSupport!!.executeSuspendingWriteAction {
        launch {
          readAction {
            checkpoint(1)
            if (wasCanceled.get()) {
              checkpoint(4)
            }
            try {
              while (true) {
                ProgressManager.checkCanceled()
              }
            } catch (_: ProcessCanceledException) {
              wasCanceled.set(true)
            }
          }
        }
        checkpoint(2)
      }
    }
    checkpoint(3)
  }

  @Test
  fun `write-intent lock is not released inside write action`(): Unit = timeoutRunBlocking {
    edtWriteAction {
      assertTrue { application.isWriteAccessAllowed }
      assertTrue { application.isWriteIntentLockAcquired }
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
        assertTrue { application.isWriteAccessAllowed }
        assertTrue { application.isWriteIntentLockAcquired }
      }
      assertTrue { application.isWriteAccessAllowed }
      assertTrue { application.isWriteIntentLockAcquired }
    }
  }

  @Test
  fun `release of WI inside suspending write action does not lead to broken IDE state`(): Unit = timeoutRunBlocking {
    readAction {  } // init internal structures
    edtWriteAction {
      assertTrue { application.isWriteAccessAllowed }
      ApplicationManagerEx.getApplicationEx().threadingSupport!!.executeSuspendingWriteAction {
        assertFalse { application.isWriteAccessAllowed }
        assertTrue { application.isWriteIntentLockAcquired }
        TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
          assertFalse { application.isWriteAccessAllowed }
          assertFalse { application.isWriteIntentLockAcquired }
        }
        assertFalse { application.isWriteAccessAllowed }
        assertTrue { application.isWriteIntentLockAcquired }
      }
      assertTrue { application.isWriteAccessAllowed }
      assertTrue { application.isWriteIntentLockAcquired }
    }
    withContext(Dispatchers.EDT) {} // check that WI can be acquired again
  }

  /**
   * Regression test for a broken lock state after a suspending write action.
   *
   * [com.intellij.openapi.application.ThreadingSupport.executeSuspendingWriteAction] downgrades the write lock to a write-intent lock.
   * When the action finishes, the lock takes the write permit back. This wait must not be cancellable.
   * The wait used to run with the cancellable context job of the write action. A cancelled job aborted the wait
   * and skipped the restore of the write-action stack base. After that, no later write action fired `beforeWriteActionStart`,
   * so reads with write-action priority were never cancelled, and the next write action that waited for such a read froze the IDE.
   *
   * The read action below holds a read permit when the write permit is taken back, so the wait has to suspend.
   */
  @Suppress("DEPRECATION")
  @Test
  fun `cancelled context job does not break reacquisition of write lock after suspending write action`(): Unit =
    timeoutRunBlocking(context = Dispatchers.Default, timeout = 30.seconds) {
      val application = ApplicationManagerEx.getApplicationEx()
      val readStarted = CountDownLatch(1)
      // the write action runs with this job as its context job; the job gets cancelled while the write lock is downgraded
      val writeActionJob = Job()
      withContext(Dispatchers.EDT) {
        installThreadContext(currentThreadContext() + writeActionJob, true) {
          runWriteAction {
            application.threadingSupport.executeSuspendingWriteAction {
              launch(Dispatchers.Default) {
                runReadAction {
                  readStarted.countDown()
                  // hold the read permit until the suspending write action starts to take the write lock back
                  val deadlineNs = System.nanoTime() + 10.seconds.inWholeNanoseconds
                  while (!application.isWriteActionPending && System.nanoTime() < deadlineNs) {
                    Thread.sleep(1)
                  }
                  // keep the permit a bit longer, so that the write lock acquisition has to wait for this read action
                  Thread.sleep(200)
                }
              }
              readStarted.await()
              writeActionJob.cancel()
            }
            assertTrue(application.isWriteAccessAllowed, "write access must be restored after the suspending write action")
          }
        }
      }

      // the next write action must still cancel reads with write-action priority, which relies on `beforeWriteActionStart`
      val beforeWriteActionStartCalls = AtomicInteger()
      val listener = object : WriteActionListener {
        override fun beforeWriteActionStart(action: Class<*>) {
          beforeWriteActionStartCalls.incrementAndGet()
        }
      }
      Disposer.newDisposable().use { disposable ->
        application.addWriteActionListener(listener, disposable)
        edtWriteAction { }
      }
      Assertions.assertEquals(1, beforeWriteActionStartCalls.get(),
                              "beforeWriteActionStart must fire for a write action that follows a suspending write action with a cancelled job")
    }
}
