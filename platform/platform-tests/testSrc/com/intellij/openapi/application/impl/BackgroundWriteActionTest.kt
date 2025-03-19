// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFalse

@TestApplication
class BackgroundWriteActionTest {

  companion object {
    @BeforeAll
    @JvmStatic
    fun ensureBackgroundWriteActionEnabled() {
      Assumptions.assumeTrue(useBackgroundWriteAction) {
        "This test suite requires enabled background write actions"
      }
    }

    @BeforeAll
    @JvmStatic
    fun ensureLockParallelizationEnabled() {
      Assumptions.assumeTrue(useNestedLocking) {
        "This test suite requires enabled lock parallelization"
      }
    }
  }

  @Test
  fun primitive(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      backgroundWriteAction {
        assertNotEdt()
      }
    }
  }

  @Test
  fun exclusion(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val readCounter = AtomicInteger()
    val writeCounter = AtomicInteger()

    fun assertExclusive(read: Boolean) {
      if (read) {
        readCounter.incrementAndGet()
        val writeValue = writeCounter.get()
        assertThat(writeValue).isEqualTo(0)
        readCounter.decrementAndGet()
      }
      else {
        val writeValue = writeCounter.incrementAndGet()
        val readValue = readCounter.get()
        assertThat(readValue).isEqualTo(0)
        assertThat(writeValue).isEqualTo(1)
        writeCounter.decrementAndGet()
      }
    }

    repeat(1000) {
      launch {
        backgroundWriteAction {
          assertExclusive(read = false)
        }
      }
      launch {
        readAction {
          assertExclusive(read = true)
        }
      }
      launch(Dispatchers.EDT) {
        readAction {
          assertExclusive(read = true)
        }
      }
      launch(Dispatchers.EDT) {
        edtWriteAction {
          assertExclusive(read = false)
        }
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  fun modality(action: suspend CoroutineScope.() -> Unit) = runWithModalProgressBlocking(ModalTaskOwner.guess(), "", action = action)

  suspend fun modalProgress(action: suspend CoroutineScope.() -> Unit) = withContext(Dispatchers.EDT) {
    modality(action)
  }

  fun assertNoRead() {
    Assertions.assertFalse(application.isReadAccessAllowed)
  }

  fun assertNoWrite() {
    Assertions.assertFalse(application.isWriteAccessAllowed)
  }

  fun assertNoWil() {
    Assertions.assertFalse(application.isWriteIntentLockAcquired)
  }

  fun assertRead() {
    Assertions.assertTrue(application.isReadAccessAllowed)
  }

  fun assertWrite() {
    Assertions.assertTrue(application.isWriteAccessAllowed)
  }

  fun assertWil() {
    Assertions.assertTrue(application.isWriteIntentLockAcquired)
  }

  fun assertNotEdt() {
    Assertions.assertFalse(EDT.isCurrentThreadEdt())
  }

  fun assertEdt() {
    Assertions.assertTrue(EDT.isCurrentThreadEdt())
  }

  @Test
  fun `modal progress with pending bg wa`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      backgroundWriteAction {}
    }
    modalProgress {
      checkpoint(1)
      assertNoWrite()
      assertNoWil()
      backgroundWriteAction {
        checkpoint(3)
        assertNotEdt()
      }
      checkpoint(4)
    }
  }

  @Test
  fun `modal progress with pending edt wa`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      edtWriteAction {
        checkpoint(5)
      }
    }
    modalProgress {
      checkpoint(1)
      assertNoWrite()
      assertNoWil()
      backgroundWriteAction {
        checkpoint(3)
        assertNotEdt()
      }
      checkpoint(4)
    }
  }

  @Test
  fun `bg wa in modal progress with pending ra`(): Unit = concurrencyTest {
    val writeActionTaken = AtomicBoolean(false)
    val readActionTaken = AtomicBoolean(false)
    val writeActionCompleted = AtomicBoolean(false)
    launch {
      readAction {
        try {
          readActionTaken.set(true)
          if (!writeActionCompleted.get()) {
            checkpoint(1)
            while (true) {
              assertFalse(writeActionTaken.get())
              Cancellation.checkCancelled()
            }
          }
        }
        finally {
          readActionTaken.set(false)
        }
      }
    }
    modalProgress {
      checkpoint(2)
      assertNoWrite()
      assertNoWil()
      backgroundWriteAction {
        writeActionTaken.set(true)
        checkpoint(3)
        assertNotEdt()
        assertFalse(readActionTaken.get())
        writeActionTaken.set(false)
        writeActionCompleted.set(true)
      }
      checkpoint(4)
    }
  }

  @Test
  fun `double modal progress`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      backgroundWriteAction {}
      checkpoint(8)
    }
    modalProgress {
      launch {
        checkpoint(1)
        backgroundWriteAction {}
        checkpoint(6)
      }
      modalProgress {
        checkpoint(3)
        backgroundWriteAction {
          checkpoint(4)
          assertNotEdt()
        }
        checkpoint(5)
      }
      checkpoint(7)
    }
    checkpoint(9)
  }

  @Test
  fun `edt and bg wa interoperation`(): Unit = concurrencyTest {
    launch {
      checkpoint(1)
      backgroundWriteAction {
        checkpoint(2)
        checkpoint(4)
      }
      checkpoint(7)
    }
    launch {
      checkpoint(3)
      checkpoint(5)
      edtWriteAction {
        checkpoint(6)
        checkpoint(8)
      }
      checkpoint(9)
    }
  }

  @Test
  fun `invokeLater in modal progress`(): Unit = concurrencyTest {
    modalProgress {
      launch {
        checkpoint(2)
        backgroundWriteAction {
          checkpoint(4)
        }
        checkpoint(6)
      }
      application.invokeLater {
        WriteAction.run<Throwable> {
          checkpoint(1)
        }
        checkpoint(3)
      }
      checkpoint(5)
      application.invokeLater {
        checkpoint(7)
        WriteAction.run<Throwable> {
          checkpoint(8)
        }
      }
    }
  }

  @Test
  fun `multiple background wa`(): Unit = concurrencyTest {
    launch {
      checkpoint(1)
      backgroundWriteAction {
        checkpoint(2)
      }
      checkpoint(4)
    }
    launch {
      checkpoint(3)
      backgroundWriteAction {
        checkpoint(5)
      }
    }
  }

  @Test
  fun `read action in modality can proceed when background wa is running`(): Unit = concurrencyTest {
    launch {
      checkpoint(3)
      backgroundWriteAction {
        checkpoint(8)
      }
    }
    modalProgress {
      launch {
        checkpoint(2)
        backgroundWriteAction {
          checkpoint(5)
        }
      }
      modalProgress {
        checkpoint(1)
        readAction {
          checkpoint(4)
        }
      }
      checkpoint(6)
      readAction {
        checkpoint(7)
      }
    }
  }

  @Test
  fun `low-level read actions can proceed when modal progress is active`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      readAction {
        checkpoint(4)
      }
      checkpoint(6)
    }
    modalProgress {
      checkpoint(1)
      checkpoint(3)
      readAction {
        checkpoint(5)
      }
      checkpoint(7)
    }
  }

  @Test
  fun `mid-level read actions are not aborted by a low-level write action`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      backgroundWriteAction {
      }
    }
    modalProgress {
      launch {
        checkpoint(3)
        readAction {
          checkpoint(5)
        }
      }
      modalProgress {
        checkpoint(1)
        readAction {
          checkpoint(4)
        }
      }
    }
  }

  @Test
  fun `mid-level read actions are aborted by a mid-level write action`(): Unit = concurrencyTest {
    launch {
      checkpoint(2)
      checkpoint(5)
      backgroundWriteAction {
        checkpoint(11)
      }
    }
    modalProgress {
      launch {
        checkpoint(3)
        checkpoint(7)
        readAction {
          checkpoint(10)
        }
      }
      launch {
        checkpoint(4)
        checkpoint(6)
        backgroundWriteAction {
          checkpoint(10)
        }
      }
      modalProgress {
        checkpoint(1)
        checkpoint(8)
        readAction {
          checkpoint(9)
        }
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod", "OPT_IN_USAGE")
  @Test
  fun `switch to EDT under read action does not cause an error`() = concurrencyTest {
    // unfortunately, our action actions subsystem contains highly non-idiomatic code like the following
    // even if this code is questionable, we will support the old contracts for now
    runBlockingCancellable {
      readAction {
        GlobalScope.launch(currentThreadContext() + Dispatchers.EDT) {
        }
      }.join()
    }
  }

  @Test
  fun `write lock parallelization`() = timeoutRunBlocking {
    edtWriteAction {
      runBlockingCancellable {
        assertRead()
        assertNoWrite()
        assertNoWil()
      }
    }
  }

  @Test
  fun `opaqueness of suspending write action`() = concurrencyTest {
    val backgroundWaRunning = AtomicBoolean(false)
    launch {
      checkpoint(2)
      checkpoint(4)
      backgroundWriteAction {
        backgroundWaRunning.set(true)
        checkpoint(9)
      }
    }
    checkpoint(1)
    edtWriteAction {
      checkpoint(3)
      (application as ApplicationImpl).executeSuspendingWriteAction(null, "") {
        checkpoint(5)
        Thread.sleep(1000)
        assertFalse(backgroundWaRunning.get())
        checkpoint(6)
      }
      checkpoint(7)
    }
    checkpoint(8)
  }
}