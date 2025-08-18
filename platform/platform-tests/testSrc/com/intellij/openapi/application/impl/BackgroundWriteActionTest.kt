// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.ui.EDT
import io.kotest.assertions.withClue
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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
  fun exclusion(): Unit = timeoutRunBlocking(context = Dispatchers.Default, timeout = 30.seconds) {
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

    repeat(2000) {
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
        assertWrite() // since it is invoked on a thread with permission to write
        assertWil()
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

  @RepeatedTest(100)
  fun `write access allowed inside explicit WA`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    repeat(1000) {
      launch {
        edtWriteAction {
          ApplicationManager.getApplication().assertWriteAccessAllowed()
        }
      }
      launch {
        backgroundWriteAction {
          ApplicationManager.getApplication().assertWriteAccessAllowed()
        }
      }
    }
  }

  @Test
  fun `prevention of WA is thread-local`(): Unit = concurrencyTest {
    launch {
      val cleanup = getGlobalThreadingSupport().prohibitWriteActionsInside()
      try {
        checkpoint(1)
        checkpoint(4)
        assertThrows<IllegalStateException> {
          application.runWriteAction { }
        }
        checkpoint(5)
      }
      finally {
        cleanup()
      }
    }
    launch {
      checkpoint(2)
      backgroundWriteAction { // can safely start
      }
      checkpoint(3)
    }
  }

  @OptIn(ExperimentalTime::class)
  @RepeatedTest(100)
  fun `cancellation of read action with pending background WA when modality happens`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    launch(Dispatchers.EDT) {
      delay(Random.nextInt(1, 10).milliseconds)
      modalProgress {
      }
    }
    coroutineScope {
      val writeActionRun = AtomicBoolean(false)
      repeat(1) {
        launch {
          delay(Random.nextInt(1, 10).milliseconds)
          readAction {
            while (!writeActionRun.get()) {
              ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(ProgressManager.getInstance().progressIndicator)
            }
          }
        }
      }

      launch {
        delay(Random.nextInt(1, 10).milliseconds)
        backgroundWriteAction {
          writeActionRun.set(true)
        }
      }

    }
  }

  /**
   * This test is not set in stone; if you feel that the platform is ready to block same-level read actions, the feel free to adjust the test.
   */
  @Test
  fun `same-level pending WA does not prevent same-level RA`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val job1 = Job()
    launch {
      modalProgress {
        job1.join()
        delay(100)
      }
    }
    launch {
      delay(10)
      backgroundWriteAction {  // pending
        Assertions.assertTrue { job1.isCompleted }
      }
    }
    delay(50)
    readAction { // must start regardless of the pending WA, because WA is free
      job1.complete()
    }
  }

  @Test
  fun `same-level ra gets canceled when modal progress exists and there is a pending WA`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    launch {
      modalProgress {
        delay(100)
      }
    }
    val counter = AtomicInteger()
    val bgWaCompleted = AtomicBoolean(false)

    launch {
      delay(10)
      backgroundWriteAction {  // pending
        Assertions.assertTrue { counter.get() == 1 }
        bgWaCompleted.set(true)
      }
    }
    delay(50)
    readAction { // must start regardless of the pending WA, because WA is free
      if (counter.get() == 0) {
        assertFalse(bgWaCompleted.get())
        while (true) {
          try {
            Cancellation.checkCancelled()
          } catch (e : Throwable) {
            counter.incrementAndGet()
            throw e
          }
        }
      } else {
        assertTrue(bgWaCompleted.get())
      }
    }
    Assertions.assertEquals(1, counter.get())
  }

  @Test
  fun `prompt cancellation of pending wa does not break subsequent ra`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val future = Job(coroutineContext.job)
    launch {
      readAction {
        future.asCompletableFuture().join()
      }
    }
    val pendingWa = launch {
      Thread.sleep(50)
      backgroundWriteAction {
        Assertions.fail<Nothing>("Should not start")
      }
    }
    delay(10)
    pendingWa.cancelAndJoin()
    future.cancel()
    readAction {
    }
  }


  @Test
  fun `runWhenWriteActionIsCompleted executes immediately when no write action`() {
    val executed = AtomicBoolean(false)

    getGlobalThreadingSupport().runWhenWriteActionIsCompleted {
      executed.set(true)
    }

    withClue("Action should execute immediately") {
      assertThat(executed.get()).isTrue
    }
  }

  @Test
  fun `runWhenWriteActionIsCompleted is not executed when write action is running`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val executed = AtomicBoolean(false)

    val job = Job(coroutineContext.job)
    val waJob = launch {
      backgroundWriteAction {
        job.asCompletableFuture().join()
      }
    }
    delay(100)
    getGlobalThreadingSupport().runWhenWriteActionIsCompleted {
      executed.set(true)
    }
    assertThat(executed.get()).isFalse
    job.complete()
    waJob.join()
    assertThat(executed.get()).isTrue
  }


  @Test
  fun `runWhenWriteActionIsCompleted is not executed when write action is pending`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val executed = AtomicBoolean(false)
    val job = Job(coroutineContext.job)
    launch {
      readAction {
        job.asCompletableFuture().join()
      }
    }
    delay(50)
    val waJob = launch {
      backgroundWriteAction {
      }
    }
    delay(50)
    getGlobalThreadingSupport().runWhenWriteActionIsCompleted {
      executed.set(true)
    }
    assertThat(executed.get()).isFalse
    job.complete()
    waJob.join()
    assertThat(executed.get()).isTrue
  }

  @Test
  fun `conditional invokeLater with read action`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val job = Job(coroutineContext.job)
    application.invokeLater({}, {
      if (EDT.isCurrentThreadEdt()) {
        job.asCompletableFuture().join()
        runReadAction { true }
      }
      else {
        false
      }
    })
    backgroundWriteAction {
      job.complete()
      application.invokeLater {}
    }
  }

  @Test
  fun `runWhenWriteActionIsCompleted is executed when lock is parallelized`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val executed = AtomicBoolean(false)
    val job = Job(coroutineContext.job)
    launch(Dispatchers.Default) {
      readAction {
        job.asCompletableFuture().join()
      }
    }
    Thread.sleep(50)
    val waJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
      }
    }
    Thread.sleep(50)
    getGlobalThreadingSupport().runWhenWriteActionIsCompleted {
      executed.set(true)
    }
    assertThat(executed.get()).isFalse
    val clenanup = getGlobalThreadingSupport().getPermitAsContextElement(currentThreadContext(), true).second
    try {
      assertThat(executed.get()).isTrue
    } finally {
      clenanup()
    }
    job.complete()
    waJob.join()
  }


  @Test
  fun `no thread starvation because of many suspended read action`(): Unit = timeoutRunBlocking {
    Assumptions.assumeTrue(useTrueSuspensionForWriteAction) {
      "Without true suspension, thread starvation is difficult to overcome"
    }
    val job = Job(coroutineContext.job)
    launch(Dispatchers.EDT) {
      job.asCompletableFuture().join()
    }
    delay(50)
    repeat(1000) {
      launch(Dispatchers.Default) {
        backgroundWriteAction {}
      }
    }
    delay(100)
    withContext(Dispatchers.Default) {
      // pending background write action should not stop this computation from execution
    }
    job.complete()
  }

  @Test
  fun `invokeAndWait works in post-write-action listener`() = timeoutRunBlocking {
    val threadingSupport = getGlobalThreadingSupport()
    val listener = object : WriteActionListener {
      override fun afterWriteActionFinished(action: Class<*>) {
        application.invokeAndWait { }
      }
    }
    try {
      threadingSupport.addWriteActionListener(listener)
      backgroundWriteAction {
      }
    }
    finally {
      threadingSupport.removeWriteActionListener(listener)
    }
  }
}