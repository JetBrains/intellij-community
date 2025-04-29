// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl


import com.intellij.openapi.application.*
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.ImplicitBlockingContextTest
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.io.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFalse


private const val REPETITIONS: Int = 100

@TestApplication
@ExtendWith(ImplicitBlockingContextTest.Enabler::class)
class ReadWritePropagationTest {
  private fun checkInheritanceViaStructureConcurrency(wrapper: suspend (() -> Unit) -> Unit, checker: () -> Boolean): Unit = timeoutRunBlocking {
    wrapper {
      assertTrue(checker())
      runBlockingCancellable {
        assertTrue(checker())
        launch {
          assertTrue(checker())
        }
        launch {
          assertTrue(checker())
        }
        assertTrue(checker())
      }
      assertTrue(checker())
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is inherited by structured concurrency`() {
    checkInheritanceViaStructureConcurrency(::readAction, { ApplicationManager.getApplication().isReadAccessAllowed })
  }

  @RepeatedTest(REPETITIONS)
  fun `write intent read action is inherited by structured concurrency`() {
    Assumptions.assumeFalse(useNestedLocking) { "This is not the intended behavior when the lock is parallelizable" }
    checkInheritanceViaStructureConcurrency(::writeIntentReadAction, { ApplicationManager.getApplication().isWriteIntentLockAcquired })
  }

  @RepeatedTest(REPETITIONS)
  fun `write action is inherited by structured concurrency`() {
    Assumptions.assumeFalse(useNestedLocking) { "This is not the intended behavior when the lock is parallelizable" }
    checkInheritanceViaStructureConcurrency(::edtWriteAction, { ApplicationManager.getApplication().isWriteAccessAllowed })
  }

  private fun checkInheritanceViaNewContext(wrapper: suspend (() -> Unit) -> Unit, checker: () -> Boolean, innerChecker: () -> Boolean = checker): Unit = timeoutRunBlocking {
    wrapper {
      assertTrue(checker())
      runBlockingCancellable {
        assertTrue(checker())
        launch(Dispatchers.Default) {
          assertTrue(innerChecker())
        }
        launch(Dispatchers.IO) {
          assertTrue(innerChecker())
        }
        assertTrue(checker())
      }
      assertTrue(checker())
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is inherited by new context`() {
    checkInheritanceViaNewContext(::readAction, { ApplicationManager.getApplication().isReadAccessAllowed })
  }

  @RepeatedTest(REPETITIONS)
  fun `write intent read action is inherited by new context`() {
    Assumptions.assumeFalse(useNestedLocking) { "This is not the intended behavior when the lock is parallelizable" }
    // WIL check works only on owning thread
    checkInheritanceViaNewContext(::writeIntentReadAction,
                                  { ApplicationManager.getApplication().isWriteIntentLockAcquired },
                                  { ApplicationManager.getApplication().isReadAccessAllowed })
  }

  private fun checkNoInheritanceViaNonStructuredConcurrency(wrapper: suspend (() -> Unit) -> Unit, checker: () -> Boolean): Unit = timeoutRunBlocking {
    wrapper {
      assertTrue(checker())
      runBlockingCancellable {
        assertTrue(checker())
        // No scope at all, so no lock
        ApplicationManager.getApplication().executeOnPooledThread {
          assertFalse(checker())
        }.await()
        assertTrue(checker())
      }
      // Scope from timeoutRunBlocking (outside RA), so no RA even if syntactically in RA
      launch {
        assertFalse(checker())
      }
      assertTrue(checker())
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read action is not inherited by non-structured concurrency`() {
    checkNoInheritanceViaNonStructuredConcurrency(::readAction, { ApplicationManager.getApplication().isReadAccessAllowed })
  }

  @RepeatedTest(REPETITIONS)
  fun `write intent read action is not inherited by non-structured concurrency`() {
    Assumptions.assumeFalse(useNestedLocking) { "This is not the intended behavior when the lock is parallelizable" }
    checkNoInheritanceViaNonStructuredConcurrency(::writeIntentReadAction, { ApplicationManager.getApplication().isWriteIntentLockAcquired })
  }

  @RepeatedTest(REPETITIONS)
  fun `write action is not inherited by non-structured concurrency`() {
    Assumptions.assumeFalse(useNestedLocking) { "This is not the intended behavior when the lock is parallelizable" }
    checkNoInheritanceViaNonStructuredConcurrency(::edtWriteAction, { ApplicationManager.getApplication().isWriteAccessAllowed })
  }

  @RepeatedTest(REPETITIONS)
  fun `nested read action can be run even if write is waiting`(): Unit = timeoutRunBlocking {
    val writePending = beforeWrite()
    val readTaskReady = Semaphore(1)
    val ra = launch(Dispatchers.Default) {
      ApplicationManager.getApplication().runReadAction {
        readTaskReady.up()
        runBlockingCancellable {
          withContext(Dispatchers.Default) {
            writePending.waitFor()
            ApplicationManager.getApplication().runReadAction {
              assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
            }
          }
        }
      }
    }
    readTaskReady.waitFor()
    val wa = launch(Dispatchers.Default) {
      assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
      ApplicationManager.getApplication().invokeAndWait {
        ApplicationManager.getApplication().runWriteAction {
          assertTrue(ApplicationManager.getApplication().isWriteIntentLockAcquired)
        }
      }
    }
    joinAll(ra, wa)
  }

  @RepeatedTest(REPETITIONS)
  fun `nested read action can be run under modal progress even if write is waiting`(): Unit = timeoutRunBlocking {
    val writePending = beforeWrite()
    val readTaskReady = Semaphore(1)
    val ra = launch(Dispatchers.EDT) {
      ApplicationManager.getApplication().runReadAction {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          readTaskReady.up()
          writePending.waitFor()
          ApplicationManager.getApplication().runReadAction {
            assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
          }
        }
      }
    }
    readTaskReady.waitFor()
    val wa = launch(Dispatchers.Default) {
      assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
      getGlobalThreadingSupport().runWriteAction(Runnable::class.java) {
        assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed)
      }
    }
    joinAll(ra, wa)
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

