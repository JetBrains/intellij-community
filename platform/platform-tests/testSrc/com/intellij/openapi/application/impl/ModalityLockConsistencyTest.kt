// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.function.Function
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

@TestApplication
class ModalityLockConsistencyTest {

  private val threadingSupport: ThreadingSupport
    get() = application.threadingSupport

  private suspend fun currentModality(): ModalityState {
    return checkNotNull(currentCoroutineContext().contextModality()) { "The context must have a modality state" }
  }

  /**
   * Runs [action] on a pooled thread under a modal [ProgressWindow].
   * [ProgressRunner] starts the progress from the UI thread with the write-intent lock.
   */
  private suspend fun <T> runInModalProgressRunner(action: (ProgressIndicator) -> T): T {
    return withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val result = ProgressRunner(Function<ProgressIndicator, T> { indicator -> action(indicator) })
          .onThread(ProgressRunner.ThreadToUse.POOLED)
          .withProgress(ProgressWindow(false, null))
          .modal()
          .submitAndGet()
        result.throwable?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        (result.result as T)
      }
    }
  }

  private fun ProgressIndicator.modalModality(): ModalityState {
    val modality = modalityState
    Assertions.assertNotEquals(ModalityState.nonModal(), modality, "A modal progress must have its own modality state")
    return modality
  }

  @Test
  fun `modal progress associates its modality with its lock`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          val modality = currentModality()
          Assertions.assertNotEquals(ModalityState.nonModal(), modality)
          Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality),
                                "The context of a modal progress must have the lock element of its modality")
          @Suppress("OPT_IN_USAGE")
          GlobalScope.launch(modality.asContextElement()) {
            Assertions.assertFalse(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality),
                                  "The context of a detached coroutine should not agree with modality")
          }.join()
        }
      }
    }
  }

  @Test
  fun `nested modal progresses`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          val outerModality = currentModality()
          withContext(Dispatchers.EDT) {
            writeIntentReadAction {
              runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
                val innerModality = currentModality()
                Assertions.assertNotSame(outerModality, innerModality)
                Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(innerModality))
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `association ends after modal progress`() = timeoutRunBlocking {
    val modality = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          val modality = currentModality()
          Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
          modality
        }
      }
    }
    Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality),
                          "The modality must have no associated lock element after the modal progress ends")
  }

  @Test
  fun `modal progress runner associates its modality with its lock`() = timeoutRunBlocking {
    runInModalProgressRunner { indicator ->
      val modality = indicator.modalModality()
      Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality),
                            "The task of a modal progress runner must have the lock element of its modality")
      var consistentInDetachedThread = true
      thread {
        consistentInDetachedThread = ModalityLockAssociation.isCurrentLockConsistentWithModality(modality)
      }.join()
      Assertions.assertFalse(consistentInDetachedThread,
                             "A thread without the lock element of the modal progress must not agree with the modality")
    }
  }

  @Test
  fun `association ends after modal progress runner`() = timeoutRunBlocking {
    val modality = runInModalProgressRunner { indicator ->
      val modality = indicator.modalModality()
      Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
      modality
    }
    Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality),
                          "The modality must have no associated lock element after the modal progress runner ends")
  }

  @Test
  fun `edt write action in a modal progress runner does not report errors`() = timeoutRunBlocking {
    val result = runInModalProgressRunner { indicator ->
      indicator.modalModality()
      runBlockingCancellable {
        edtWriteAction {
          Assertions.assertTrue(application.isWriteAccessAllowed)
          42
        }
      }
    }
    Assertions.assertEquals(42, result)
  }

  @Test
  fun `edt write action reports a lock element that does not correspond to the modality of a progress runner`() = timeoutRunBlocking {
    val reportedError = CompletableDeferred<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        if (message.contains("does not correspond to the modality state")) {
          reportedError.complete(message)
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }
    LoggedErrorProcessor.executeWith(errorProcessor).use {
      runInModalProgressRunner { indicator ->
        val modality = indicator.modalModality()
        runBlockingCancellable {
          // the modality of the modal progress leaks to a coroutine without the lock element of the progress;
          // this write action cannot start until the modal progress ends, so we cancel it after the check
          @Suppress("OPT_IN_USAGE")
          val job = GlobalScope.launch(modality.asContextElement()) {
            edtWriteAction {}
          }
          reportedError.await()
          job.cancel()
        }
      }
    }
  }

  @Test
  fun `explicit association`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val modality = ModalityState.nonModal()
        val (parallelizedLockContext, parallelizationCleanup) = threadingSupport.parallelizeLock(false)
        try {
          ModalityLockAssociation.associate(modality, parallelizedLockContext).use {
            Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
          }
          Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
        }
        finally {
          parallelizationCleanup()
        }
        // lock is cleaned up at this point
        Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
      }
    }
  }

  @Test
  fun `context without lock element is not associated`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val modality = ModalityState.nonModal()
        Assertions.assertTrue(ModalityLockAssociation.isCurrentLockConsistentWithModality(modality))
      }
    }
  }

  @Test
  fun `edt write action in a modal progress does not report errors`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          val result = edtWriteAction {
            Assertions.assertTrue(application.isWriteAccessAllowed)
            42
          }
          Assertions.assertEquals(42, result)
        }
      }
    }
  }

  @Test
  fun `edt write action reports a lock element that does not correspond to the modality`() = timeoutRunBlocking {
    val reportedError = CompletableDeferred<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        if (message.contains("does not correspond to the modality state")) {
          reportedError.complete(message)
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }
    LoggedErrorProcessor.executeWith(errorProcessor).use {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          val outerLockContext: CoroutineContext = threadingSupport.getLockContextElement()
          runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
            // the modality of the modal progress leaks with the lock element of the outer computation;
            // this write action cannot start until the modal progress ends, so we cancel it after the check
            val job = async(outerLockContext) {
              edtWriteAction {
                42
              }
            }
            reportedError.await()
            Assertions.assertEquals(42, job.await())
          }
        }
      }
    }
  }
}
