// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
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
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
  fun foo(): Unit = concurrencyTest {
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
}
