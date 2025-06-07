// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.SuvorovProgress
import com.intellij.openapi.project.DumbAware
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class PlatformUtilitiesTest {

  @Test
  fun `relaxing preventive actions leads to absence of lock`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContext(Dispatchers.ui(kind = UiDispatcherKind.RELAX)) {
      assertThat(application.isWriteIntentLockAcquired).isFalse
      getGlobalThreadingSupport().runPreventiveWriteIntentReadAction {
        assertThat(application.isWriteIntentLockAcquired).isTrue
      }
      getGlobalThreadingSupport().relaxPreventiveLockingActions {
        getGlobalThreadingSupport().runPreventiveWriteIntentReadAction {
          assertThat(application.isWriteIntentLockAcquired).isFalse
        }
      }

    }

  }

  @Test
  fun `invokeAndWaitRelaxed does not take lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    ApplicationManagerEx.getApplicationEx().invokeAndWaitRelaxed(
      {
        assertThat(application.isWriteAccessAllowed).isFalse()
        assertThat(application.isReadAccessAllowed).isFalse()
        assertThat(application.isWriteIntentLockAcquired).isFalse()
        assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue()
      }, ModalityState.nonModal())
  }

  @Test
  fun `raw background write action is not allowed`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    assertThrows<IllegalStateException> {
      ApplicationManager.getApplication().runWriteAction {
        fail<Nothing>()
      }
    }
  }

  @Test
  fun `edt wa means that background wa is not pending`(): Unit = concurrencyTest {
    launch {
      edtWriteAction {
        checkpoint(1)
        checkpoint(4)
      }
    }
    checkpoint(2)
    assertThat(ApplicationManagerEx.getApplicationEx().isBackgroundWriteActionRunningOrPending).isFalse
    checkpoint(3)
  }

  @Test
  fun `bg wa means that background wa is not pending`(): Unit = concurrencyTest {
    Assumptions.assumeTrue(useBackgroundWriteAction)
    launch {
      backgroundWriteAction {
        checkpoint(1)
        checkpoint(4)
      }
    }
    checkpoint(2)
    assertThat(ApplicationManagerEx.getApplicationEx().isBackgroundWriteActionRunningOrPending).isTrue
    checkpoint(3)
  }

  private class DummyAction() : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  @Test
  fun `action can be updated when background write action is in progress under write intent`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    withContext(Dispatchers.EDT) {
      launch(Dispatchers.Default) {
        backgroundWriteAction {
        }
      }
      Thread.sleep(50)
      ActionManager.getInstance().tryToExecute(DummyAction(), null, null, null, true)
    }
  }

  @Test
  fun `reacquisition of write-intent lock is not promptly cancellable`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val infiniteJob = Job(currentCoroutineContext().job)
    val jobWaiting = Job(currentCoroutineContext().job)
    val coroutine = launch(Dispatchers.EDT) {
      getGlobalThreadingSupport().releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
        jobWaiting.complete()
        infiniteJob.asCompletableFuture().join()
      }
    }
    jobWaiting.join()
    infiniteJob.cancel()
    coroutine.cancelAndJoin()
  }

  @Test
  fun `non-blocking read action is cancellable inside a non-cancellable section`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val job = Job(coroutineContext.job)
    val job2 = Job(coroutineContext.job)
    val counter = AtomicInteger(0)
    val nbraJob = launch {
      Cancellation.executeInNonCancelableSection {
        ReadAction.nonBlocking {
          job2.complete()
          job.asCompletableFuture().join()
          try {
            ProgressManager.checkCanceled()
          }
          catch (e: ProcessCanceledException) {
            counter.incrementAndGet()
            throw e
          }
        }.executeSynchronously()
      }
    }
    job2.join()
    launch {
      writeAction { }
    }
    delay(50)
    job.complete()
    nbraJob.join()
    assertThat(counter.get()).isEqualTo(1)
  }

  @Test
  fun `non-blocking read action is cancellable inside a non-cancellable section 2`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val beforeWaJob = Job(coroutineContext.job)
    val waJob = Job(coroutineContext.job)
    val counter = AtomicInteger(0)
    launch {
      beforeWaJob.join()
      writeAction {
        waJob.asCompletableFuture().join()
      }
    }
    val nbraJob = launch {
      Cancellation.withNonCancelableSection().use {
        readAction {
          ReadAction.nonBlocking {
            try {
              beforeWaJob.complete()
              Thread.sleep(100)
              Cancellation.checkCancelled()
              if (counter.get() == 0) {
                fail<Nothing>()
              }
            }
            catch (e: ProcessCanceledException) {
              counter.incrementAndGet()
              throw e
            }
          }.executeSynchronously()
        }
      }
    }
    delay(50)
    waJob.complete()
    nbraJob.join()
    assertThat(counter.get()).isEqualTo(1)
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `transferredWriteAction allows write access when lock action is pending`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    application.invokeAndWait {
      getGlobalThreadingSupport().setLockAcquisitionInterceptor(SuvorovProgress::dispatchEventsUntilComputationCompletes)
    }
    try {
      val bgWaStarted = Job(coroutineContext.job)
      launch {
        backgroundWriteAction {
          bgWaStarted.complete()
          Thread.sleep(100) // give chance EDT to start waiting for a coroutine
          (application as ApplicationImpl).invokeAndWaitWithTransferredWriteAction {
            assertThat(EDT.isCurrentThreadEdt()).isTrue
            assertThat(application.isWriteAccessAllowed).isTrue
            runWriteAction {}
            assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
          }
        }
      }
      bgWaStarted.join()
      launch(Dispatchers.EDT) {
      }
    }
    finally {
      application.invokeAndWait {
        getGlobalThreadingSupport().removeLockAcquisitionInterceptor()
      }
    }
  }

  @Test
  fun `transferredWriteAction can run as invokeAndWait`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    backgroundWriteAction {
      (application as ApplicationImpl).invokeAndWaitWithTransferredWriteAction {
        assertThat(EDT.isCurrentThreadEdt()).isTrue
        assertThat(application.isWriteAccessAllowed).isTrue
        runWriteAction {}
        assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
      }
    }
  }

  @Test
  fun `transferredWriteAction is not available without write lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    assertThrows<AssertionError> {
      (application as ApplicationImpl).invokeAndWaitWithTransferredWriteAction {
        fail<Nothing>()
      }
    }
  }

  @Test
  fun `transferredWriteAction is not available on EDT`(): Unit = timeoutRunBlocking(context = Dispatchers.ui(UiDispatcherKind.RELAX)) {
    assertThrows<AssertionError> {
      (application as ApplicationImpl).invokeAndWaitWithTransferredWriteAction {
        fail<Nothing>()
      }
    }
  }

  @Test
  fun `transferredWriteAction rethrows exceptions`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    backgroundWriteAction {
      val exception = assertThrows<IllegalStateException> {
        (application as ApplicationImpl).invokeAndWaitWithTransferredWriteAction {
          throw IllegalStateException("custom message")
        }
      }
      assertThat(exception.message).isEqualTo("custom message")
    }
  }
}