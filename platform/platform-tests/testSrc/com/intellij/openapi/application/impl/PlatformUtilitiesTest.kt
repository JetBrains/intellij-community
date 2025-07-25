// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class PlatformUtilitiesTest {

  @Test
  fun `relaxing preventive actions leads to absence of lock`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContext(Dispatchers.UiWithModelAccess) {
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
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
        jobWaiting.complete()
        assertThat(application.isWriteIntentLockAcquired).isFalse
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
      withContext(NonCancellable) {
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

  @Test
  fun `transferredWriteAction allows write access when lock action is pending`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    Assumptions.assumeTrue { installSuvorovProgress }
    val bgWaStarted = Job(coroutineContext.job)
    launch {
      backgroundWriteAction {
        bgWaStarted.complete()
        Thread.sleep(100) // give chance EDT to start waiting for a coroutine
        InternalThreading.invokeAndWaitWithTransferredWriteAction {
          assertThat(EDT.isCurrentThreadEdt()).isTrue
          assertThat(application.isWriteAccessAllowed).isTrue
          assertThat(application.isReadAccessAllowed).isTrue
            runWriteAction {}
            runReadAction { }
          assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
        }
      }
    }
    bgWaStarted.join()
    launch(Dispatchers.EDT) {
    }
  }

  @Test
  fun `transferredWriteAction can run as invokeAndWait`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    backgroundWriteAction {
      InternalThreading.invokeAndWaitWithTransferredWriteAction {
        assertThat(EDT.isCurrentThreadEdt()).isTrue
        assertThat(application.isWriteAccessAllowed).isTrue
        assertThat(application.isReadAccessAllowed).isTrue
        runWriteAction {}
        runReadAction { }
        assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
      }
    }
  }

  @Test
  fun `transferredWriteAction is not available without write lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    assertThrows<AssertionError> {
      InternalThreading.invokeAndWaitWithTransferredWriteAction {
        fail<Nothing>()
      }
    }
  }

  @Test
  fun `transferredWriteAction is not available on EDT`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    assertThrows<AssertionError> {
      InternalThreading.invokeAndWaitWithTransferredWriteAction {
        fail<Nothing>()
      }
    }
  }

  @Test
  fun `transferredWriteAction rethrows exceptions`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    backgroundWriteAction {
      val exception = assertThrows<IllegalStateException> {
        InternalThreading.invokeAndWaitWithTransferredWriteAction {
          throw IllegalStateException("custom message")
        }
      }
      assertThat(exception.message).isEqualTo("custom message")
    }
  }


  class CustomException : RuntimeException()

  @Test
  fun `nested old modal progress does not leak lock`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val customExceptionWasRethrown = AtomicBoolean(false)
    val writeActionThrew = AtomicBoolean(false)
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action?> {
        if (t is CustomException) {
          // rethrow exception directly
          throw t
        }
        return super.processError(category, message, details, t)
      }
    }).use {
      try {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "title1") {
          override fun run(indicator: ProgressIndicator) {
            invokeLater {
              ProgressManager.getInstance().run(object : Task.Backgroundable(null, "title2") {
                override fun run(indicator: ProgressIndicator) {
                  application.invokeLater {
                    throw CustomException()
                  }
                  try {
                    application.invokeAndWait {
                      runWriteAction {
                      }
                    }
                  }
                  catch (e: Throwable) {
                    writeActionThrew.set(true)
                  }
                }
              })
            }
          }
        })
      }
      catch (_: CustomException) {
        customExceptionWasRethrown.set(true)
      }
      try {
        UIUtil.dispatchAllInvocationEvents()
      }
      catch (e: CustomException) {
        customExceptionWasRethrown.set(true)
      }
    }
    assertThat(customExceptionWasRethrown.get()).isTrue()
    assertThat(writeActionThrew.get()).isFalse()
  }

  @Test
  fun `parallelization of write-intent lock removes write-intent access`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val (lockContext, lockCleanup) = getGlobalThreadingSupport().getPermitAsContextElement(currentThreadContext(), true)
    installThreadContext(lockContext).use {
      try {
        assertThat(application.isWriteIntentLockAcquired).isFalse
      } finally {
        lockCleanup()
      }
    }
  }

  @Test
  fun `synchronous non-blocking read action does not cause thread starvation`(): Unit = timeoutRunBlocking {
    val numberOfNonBlockingReadActions = Runtime.getRuntime().availableProcessors() * 2
    val readActionCanFinish = Job(coroutineContext.job)
    val readActionStarted = Job(coroutineContext.job)
    launch(Dispatchers.Default) {
      runReadAction {
        readActionStarted.complete()
        readActionCanFinish.asCompletableFuture().join()
      }
    }
    launch(Dispatchers.Default) {
      readActionStarted.join()
      backgroundWriteAction {  }
    }
    readActionStarted.join()
    delay(100) // let bg wa become pending
    val counter = AtomicInteger(0)
    coroutineScope {
      repeat(numberOfNonBlockingReadActions) {
        launch(Dispatchers.Default) {
          ReadAction.nonBlocking(Callable {
            counter.incrementAndGet()
          }).executeSynchronously()
        }
      }
      delay(100)
      readActionCanFinish.complete()
    }
    assertThat(counter.get()).isEqualTo(numberOfNonBlockingReadActions)
  }
}