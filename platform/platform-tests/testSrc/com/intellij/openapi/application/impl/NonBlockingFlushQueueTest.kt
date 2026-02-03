// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@TestApplication
class NonBlockingFlushQueueTest {

  lateinit var flushQueue: NonBlockingFlushQueue
  lateinit var counter: AtomicInteger

  @BeforeEach
  fun setUpFlushQueue() {
    flushQueue = NonBlockingFlushQueue(getGlobalThreadingSupport())
    counter = AtomicInteger(0)
  }

  @AfterEach
  fun tearDownFlushQueue() {
  }

  fun pushNonModalWI(runnable: Runnable) {
    flushQueue.push(ModalityState.nonModal(), runnable, true) { false }
  }

  fun pushNonModalUI(runnable: Runnable) {
    flushQueue.push(ModalityState.nonModal(), runnable, false) { false }
  }

  fun pushNonModalUIExpired(runnable: Runnable) {
    flushQueue.push(ModalityState.nonModal(), runnable, false) { true }
  }

  fun enterModal(modalEntity: Any) {
    LaterInvocator.enterModal(modalEntity)
    flushQueue.onModalityChanged()
  }

  fun leaveModal(modalEntity: Any) {
    flushQueue.onModalityChanged()
    LaterInvocator.leaveModal(modalEntity)
  }

  fun pushCurrentModalUI(runnable: Runnable) {
    val modality = LaterInvocator.getCurrentModalityState()
    flushQueue.push(modality, runnable, false) { false }
  }

  fun pushCurrentModalWI(runnable: Runnable) {
    val modality = LaterInvocator.getCurrentModalityState()
    flushQueue.push(modality, runnable, true) { false }
  }

  fun pushNonModalWIExpired(runnable: Runnable) {
    flushQueue.push(ModalityState.nonModal(), runnable, true) { true }
  }

  suspend fun spinQueue() {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {}
    suspendCancellableCoroutine { cont ->
      SwingUtilities.invokeLater {
        cont.resume(Unit, {_, _, _->})
      }
    }
  }

  @Test
  fun `ordering is preserved for identical metadata - WI`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      pushNonModalWI {
        assertTrue(EDT.isCurrentThreadEdt())
        assertEquals(0, counter.getAndIncrement())
      }
      pushNonModalWI {
        assertTrue(EDT.isCurrentThreadEdt())
        assertEquals(1, counter.getAndIncrement())
      }
    }
    spinQueue()
    assertEquals(2, counter.get())
  }

  @Test
  fun `ordering is preserved for identical metadata - UI only`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      pushNonModalUI {
        assertTrue(EDT.isCurrentThreadEdt())
        assertEquals(0, counter.getAndIncrement())
      }
      pushNonModalUI {
        assertTrue(EDT.isCurrentThreadEdt())
        assertEquals(1, counter.getAndIncrement())
      }
    }
    spinQueue()
    assertEquals(2, counter.get())
  }

  @Test
  fun `UI event may overtake WI event when WI cannot acquire lock`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    // Start a background write action that blocks WI execution on EDT
    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI) {
      bgWaStarted.join()
      pushNonModalWI {
        order.add("WI")
      }
      pushNonModalUI {
        order.add("UI")
      }
    }

    // Pump the queue: UI should run while WI is delayed
    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("UI"), order)
    }

    // Allow WI to proceed and ensure it eventually runs
    releaseBgWa.complete()
    bgJob.join()

    // Pump again to process the delayed WI
    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("UI", "WI"), order)
    }
  }

  @Test
  fun `non-modal tasks are delayed while in modal state and resume after exit`(): Unit = timeoutRunBlocking {
    val ran = CompletableFuture<Boolean>()
    val modalEntity = Any()

    withContext(Dispatchers.UI) {
      // Enter deeper modality
      enterModal(modalEntity)
      // Schedule a NON_MODAL runnable while current modality is modal
      pushNonModalUI {
        ran.complete(true)
      }
    }

    // Pump: task should NOT run yet due to modality mismatch
    spinQueue()
    assertFalse(ran.isDone)

    // Exit modality and notify our local queue about modality exit
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modalEntity)
    }

    // Now the task should be re-included and executed
    spinQueue()
    assertTrue(ran.getNow(false))
  }

  @Test
  fun `expired tasks are skipped`(): Unit = timeoutRunBlocking {
    val ran = CompletableFuture<Boolean>()
    withContext(Dispatchers.UI) {
      pushNonModalUIExpired {
        ran.complete(true)
      }
    }
    spinQueue()
    // The runnable must not be executed because it's expired
    assertFalse(ran.isDone)
  }

  @Test
  fun `modal UI runs immediately while non-modal is deferred`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modalEntity = Any()

    withContext(Dispatchers.UI) {
      enterModal(modalEntity)
      pushCurrentModalUI { order.add("modal-ui") }
      pushNonModalUI { order.add("non-modal-ui") }
    }

    // Pump once: modal-ui should execute, non-modal should wait
    spinQueue()
    assertEquals(listOf("modal-ui"), order)

    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modalEntity)
    }

    spinQueue()
    assertEquals(listOf("modal-ui", "non-modal-ui"), order)
  }

  data class ModalityWrapper(val name: String)

  @Test
  fun `nested modalities keep non-modal deferred until full exit`(): Unit = timeoutRunBlocking {
    val ran = CompletableFuture<Boolean>()
    val outer = ModalityWrapper("Outer")
    val inner = ModalityWrapper("Inner")

    withContext(Dispatchers.UI) {
      enterModal(outer)
      enterModal(inner)
      pushNonModalUI { ran.complete(true) }
    }

    spinQueue()
    assertFalse(ran.isDone)

    // Leave only inner, still in outer modality -> still deferred
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(inner)
    }
    spinQueue()
    assertFalse(ran.isDone)

    // Leave outer -> now it should run
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(outer)
    }
    spinQueue()
    withContext(Dispatchers.UI) {
      assertTrue(ran.getNow(false))
    }
  }

  @Test
  fun `WI ordering preserved across UI_ONLY then back to ALL`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI) {
      bgWaStarted.join()
      // This WI will fail to acquire, switching to UI_ONLY and be skipped
      pushNonModalWI { order.add("WI-A") }
      // UI should run while WI is delayed
      pushNonModalUI { order.add("UI-X") }
      // Another WI enqueued while still UI_ONLY; must keep A before B on resume
      pushNonModalWI { order.add("WI-B") }
    }

    spinQueue()
    // Only UI-X should have run so far
    assertEquals(listOf("UI-X"), order)

    // Release background write action so WI can proceed; queue should switch back to ALL via WriteActionFinished
    releaseBgWa.complete()
    bgJob.join()

    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("UI-X", "WI-A", "WI-B"), order)
    }
  }

  @Test
  fun `entering modality during UI_ONLY delays skipped WI until modality exit`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modal = ModalityWrapper("Have WI before")
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI) {
      bgWaStarted.join()
      // Fail WI acquisition -> UI_ONLY; this WI goes to skipped WI list
      pushNonModalWI { order.add("WI-before-modal") }
      // forcefully try to execute this event
      yield()
      // the event was delayed; we are now in UI_ONLY state
      assertTrue(order.isEmpty())
      // Enter modality while in UI_ONLY; skipped WI must be moved under skipped modality and not run until exit
      enterModal(modal)
      // Even UI in current modal should run
      pushCurrentModalUI { order.add("modal-UI") }
    }

    spinQueue()
    // Only modal UI should execute
    assertEquals(listOf("modal-UI"), order)

    // Allow WI, but still inside modality -> WI must not run yet
    releaseBgWa.complete()
    bgJob.join()
    spinQueue()
    assertEquals(listOf("modal-UI"), order)

    // Exit modality -> WI should be re-included and executed
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modal)
    }
    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("modal-UI", "WI-before-modal"), order)
    }
  }

  @Test
  fun `expired WI tasks are skipped`(): Unit = timeoutRunBlocking {
    val ran = CompletableFuture<Boolean>()
    withContext(Dispatchers.UI) {
      pushNonModalWIExpired { ran.complete(true) }
    }
    spinQueue()
    assertFalse(ran.isDone)
  }

  @Test
  fun `deferred non-modal UI preserves ordering after modality exit`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modal = ModalityWrapper("Have non-modal after")

    withContext(Dispatchers.UI) {
      enterModal(modal)
      pushNonModalUI { order.add("A") }
      pushNonModalUI { order.add("B") }
    }

    spinQueue()
    assertEquals(emptyList<String>(), order)

    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modal)
    }

    spinQueue()
    assertEquals(listOf("A", "B"), order)
  }

  @Test
  fun `WI enqueued during UI_ONLY preserve relative order on resume`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI) {
      bgWaStarted.join()
      pushNonModalWI { order.add("WI-1") }
      pushNonModalWI { order.add("WI-2") }
    }

    spinQueue()
    // While UI_ONLY, neither WI should have run
    assertEquals(emptyList<String>(), order)

    releaseBgWa.complete()
    bgJob.join()

    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("WI-1", "WI-2"), order)
    }
  }

  @Test
  fun `modal WI runs immediately and non-modal WI is deferred until modality exit`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modalEntity = ModalityWrapper("Have mixed inside")

    withContext(Dispatchers.UI) {
      enterModal(modalEntity)
      // enqueue modal WI, then non-modal WI, then one more modal WI
      pushCurrentModalWI { order.add("modal-wi-1") }
      pushNonModalWI { order.add("non-modal-wi-1") }
      pushCurrentModalWI { order.add("modal-wi-2") }
    }

    // While in modal state, only modal WI should execute and preserve their relative order
    spinQueue()
    assertEquals(listOf("modal-wi-1", "modal-wi-2"), order)

    // Exit modality, then the deferred non-modal WI should run
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modalEntity)
    }

    spinQueue()
    assertEquals(listOf("modal-wi-1", "modal-wi-2", "non-modal-wi-1"), order)
  }

  @Test
  fun `background write action inside modality - modal WI skipped until WA ends, ordered before non-modal UI on exit`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modal = ModalityWrapper("Has BG wa inside")
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      enterModal(modal)
      bgWaStarted.join()
      // Enqueue a modal WI that will fail to acquire WI due to background WA
      pushCurrentModalWI { order.add("M-WI-1") }
      // This modal UI should still run
      pushCurrentModalUI { order.add("M-UI-1") }
      // Non-modal UI is not acceptable by current modality and must be deferred until modality exit
      pushNonModalUI { order.add("NM-UI-1") }
      // Another modal WI to check ordering among WI tasks
      pushCurrentModalWI { order.add("M-WI-2") }
    }

    // First pump: only modal UI should execute, WI are skipped, non-modal UI deferred by modality
    spinQueue()
    assertEquals(listOf("M-UI-1"), order)

    // Allow WI to proceed while still inside modality: skipped modal WI should now run in order
    releaseBgWa.complete()
    bgJob.join()

    spinQueue()
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // Non-modal UI still deferred due to modality; modal WI should have executed in FIFO order
      assertEquals(listOf("M-UI-1", "M-WI-1", "M-WI-2"), order)
    }

    // Exit modality: now the deferred non-modal UI should run
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modal)
    }

    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("M-UI-1", "M-WI-1", "M-WI-2", "NM-UI-1"), order)
    }
  }

  @Test
  fun `modality exit re-includes skipped WI and skipped modality in correct order`(): Unit = timeoutRunBlocking {
    val order = ArrayList<String>()
    val modal = ModalityWrapper("Complex exit")
    val releaseBgWa = Job(coroutineContext.job)
    val bgWaStarted = Job(coroutineContext.job)

    val bgJob = launch(Dispatchers.Default) {
      backgroundWriteAction {
        bgWaStarted.complete()
        releaseBgWa.asCompletableFuture().join()
      }
    }

    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      enterModal(modal)
      bgWaStarted.join()
      // Two modal WI that will fail to acquire WI and be skipped into skippedWriteIntentQueue
      pushCurrentModalWI { order.add("M-WI-1") }
      pushCurrentModalWI { order.add("M-WI-2") }
      // Two non-modal UI that are incompatible with current modality and will be placed into skippedModalityQueue
      pushNonModalUI { order.add("NM-UI-1") }
      pushNonModalUI { order.add("NM-UI-2") }
      // A modal UI that should execute immediately before we exit modality
      pushCurrentModalUI { order.add("M-UI-1") }
    }

    // Pump once to establish skipped queues: only modal UI should run
    spinQueue()
    assertEquals(listOf("M-UI-1"), order)

    // Exit modality while still in UI_ONLY due to background WA
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      leaveModal(modal)
    }

    // On modality exit, queue re-includes skipped WI first, then skipped modality; since still UI_ONLY,
    // WI won’t run yet, but non-modal UI will now be acceptable and execute in FIFO order
    spinQueue()
    assertEquals(listOf("M-UI-1", "NM-UI-1", "NM-UI-2"), order)

    // Finish background write action; WI should now be re-included and execute in FIFO order after UI
    releaseBgWa.complete()
    bgJob.join()

    spinQueue()
    withContext(Dispatchers.UI) {
      assertEquals(listOf("M-UI-1", "NM-UI-1", "NM-UI-2", "M-WI-1", "M-WI-2"), order)
    }
  }
}
