// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertTrue

@TestApplication
class EdtCoroutineDispatcherTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  @ParameterizedTest
  @Retention(AnnotationRetention.RUNTIME)
  @MethodSource("com.intellij.openapi.application.impl.EdtCoroutineDispatcherTestKt#uiThreadDispatchers")
  private annotation class UiThreadDispatcherTest

  @UiThreadDispatcherTest
  fun `dispatch thread`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    withContext(dispatcher) {
      ThreadingAssertions.assertEventDispatchThread()
    }
  }

  @UiThreadDispatcherTest
  fun `externally cancelled coroutine`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val edtJob = launch(dispatcher) {
      delay(Long.MAX_VALUE)
    }
    edtJob.cancel()
  }

  @UiThreadDispatcherTest
  fun `internally cancelled coroutine`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    assertThrows<CancellationException> {
      withContext(Job() + dispatcher) {
        throw CancellationException()
      }
    }
  }

  @UiThreadDispatcherTest
  fun `failed coroutine`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val t = object : Throwable() {}
    val thrown = assertThrows<Throwable> {
      withContext(Job() + dispatcher) {
        throw t
      }
    }
    //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(t, thrown)
  }

  @UiThreadDispatcherTest
  fun `cancelled coroutine finishes normally ignoring modality and does not leak`(dispatcher: CoroutineContext) {
    val leak = object : Any() {
      override fun toString(): String = "leak"
    }
    val root = LaterInvocator::class.java

    ApplicationManager.getApplication().withModality {
      @OptIn(DelicateCoroutinesApi::class)
      val job = GlobalScope.launch(dispatcher + ModalityState.nonModal().asContextElement()) {
        fail(leak.toString()) // keep reference to the leak
      }
      assertReferenced(root, leak)
      timeoutRunBlocking {
        job.cancelAndJoin()
      }
      LeakHunter.checkLeak(root, leak.javaClass)
    }
  }

  @Test
  fun `dispatched runnable does not leak through uncompleted coroutine`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val job = launch {
        // this part is wrapped into DispatchedRunnable
        awaitCancellation()
        // this part is also wrapped into DispatchedRunnable
      }
      yield() // checkLeak should be executed after the coroutine suspends in awaitCancellation
      @Suppress("INVISIBLE_REFERENCE")
      val leakClass = DispatchedRunnable::class.java
      LeakHunter.checkLeak(job, leakClass) { dispatchedRunnable ->
        // truly leaked DispatchedRunnable instance references the Job via _completionHandle
        LeakHunter.checkLeak(dispatchedRunnable, job.javaClass)
        // if the above didn't throw, this is not really a leak,
        // e.g., an empty DispatchedRunnable referenced through IdeEventQueue.trueCurrentEvent IDEA-357405
        true
      }
      job.cancel()
    }
  }

  @UiThreadDispatcherTest
  fun `switch to EDT under read lock fails with ISE`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    readAction {
      assertThrows<IllegalStateException> {
        runBlockingMaybeCancellable {
          launch(Dispatchers.Default) {
            withContext(dispatcher) {
              fail<Nothing>()
            }
          }
        }
      }
    }
  }

  @UiThreadDispatcherTest
  fun `immediate dispatch under null modality`(dispatcher: CoroutineContext): Unit = `immediate dispatch`(dispatcher, null, null)

  @UiThreadDispatcherTest
  fun `immediate dispatch explicitly nonModal`(dispatcher: CoroutineContext): Unit = `immediate dispatch`(dispatcher, ModalityState.nonModal(), ModalityState.nonModal())

  @UiThreadDispatcherTest
  fun `explicit nonModal is immediately dispatched under null modality`(dispatcher: CoroutineContext): Unit = `immediate dispatch`(dispatcher, null, ModalityState.nonModal())

  @UiThreadDispatcherTest
  fun `null modality is immediately dispatched under explicit nonModal`(dispatcher: CoroutineContext): Unit = `immediate dispatch`(dispatcher, ModalityState.nonModal(), null)

  private fun `immediate dispatch`(dispatcher: CoroutineContext, outerModality: ModalityState?, innerModality: ModalityState?): Unit = timeoutRunBlocking {
    val eventNum = AtomicInteger()
    val events = hashSetOf<Int>()
    withContext(dispatcher.let { outerModality?.asContextElement()?.plus(it) ?: it }) {
      val job1 = launch(Dispatchers.Main.immediate.let { innerModality?.asContextElement()?.plus(it) ?: it }) {
        events += eventNum.get()
      }
      val job2 = launch(dispatcher.let { innerModality?.asContextElement()?.plus(it) ?: it }) {
        eventNum.incrementAndGet()
      }
      val job3 = launch(Dispatchers.Main.immediate.let { innerModality?.asContextElement()?.plus(it) ?: it }) {
        events += eventNum.get()
      }
      job1.join()
      job2.join()
      job3.join()
    }
    assertThat(eventNum.get()).isEqualTo(1)
    assertThat(events).hasSize(1)
  }

  @UiThreadDispatcherTest
  fun `immediate dispatch with the same modality`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val eventNum = AtomicInteger()
    val events = hashSetOf<Int>()
    val jobs = mutableListOf<Job>()
    withContext(dispatcher) {
      withModality {
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          events += eventNum.get()
        }
        jobs += launch(dispatcher + ModalityState.current().asContextElement()) {
          eventNum.incrementAndGet()
        }
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          events += eventNum.get()
        }
      }
    }
    jobs.forEach { it.join() }
    assertThat(eventNum.get()).isEqualTo(1)
    assertThat(events).hasSize(1)
  }

  @UiThreadDispatcherTest
  fun `immediate dispatch is not performed when the context modality is lower`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(dispatcher) {
      val flow = MutableSharedFlow<Int>()
      val collected = AtomicInteger()
      val modalities = hashSetOf<ModalityState>()
      jobs += launch(Dispatchers.Main.immediate + ModalityState.nonModal().asContextElement()) {
        flow.collect { value ->
          modalities += ModalityState.current()
          collected.set(value)
          if (value == 2) {
            cancel()
          }
        }
      }
      withModality {
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          flow.emit(1)
          flow.emit(2)
        }
      }
      jobs.forEach { it.join() }
      assertThat(collected.get()).isEqualTo(2)
      assertThat(modalities).containsOnly(ModalityState.nonModal())
    }
  }

  @UiThreadDispatcherTest
  fun `immediate dispatch is performed when the context modality is any`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(dispatcher) {
      val flow = MutableSharedFlow<Int>()
      val collected = AtomicInteger()
      val modalities = hashSetOf<ModalityState>()
      val collectJob = launch(Dispatchers.Main.immediate + ModalityState.any().asContextElement()) {
        flow.collect { value ->
          modalities += ModalityState.current()
          collected.set(value)
          if (value == 2) {
            cancel()
          }
        }
      }
      withModality {
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          flow.emit(1)
          flow.emit(2)
          collectJob.join()
          assertThat(collected.get()).isEqualTo(2)
          assertThat(modalities).containsOnly(ModalityState.current())
        }
      }
      jobs.forEach { it.join() }
    }
  }

  @UiThreadDispatcherTest
  fun `immediate dispatch is performed when the context modality is null`(dispatcher: CoroutineContext): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(dispatcher) {
      val flow = MutableSharedFlow<Int>()
      val collected = AtomicInteger()
      val modalities = hashSetOf<ModalityState>()
      jobs += launch(Dispatchers.Main.immediate) {
        assertThat(currentCoroutineContext().contextModality()).isNull() // Ensure that we're actually testing the no-modality case.
        flow.collect { value ->
          modalities += ModalityState.current()
          collected.set(value)
          if (value == 2) {
            cancel()
          }
        }
      }
      lateinit var innerModality: ModalityState
      withModality {
        innerModality = ModalityState.current()
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          flow.emit(1)
          flow.emit(2)
        }
      }
      jobs.forEach { it.join() }
      assertThat(collected.get()).isEqualTo(2)
      assertThat(modalities).containsOnly(innerModality)
    }
  }

  @Test
  fun `main ui dispatcher does not perform dispatch when used under edt`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertThat(application.isReadAccessAllowed).isTrue
      withContext(Dispatchers.UIImmediate) {
        assertThat(application.isReadAccessAllowed).isTrue
      }
      assertThat(application.isReadAccessAllowed).isTrue
    }
  }

  @Test
  fun `main edt dispatcher performs dispatch when used under ui`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      assertThat(application.isReadAccessAllowed).isFalse
      withContext(Dispatchers.EDTImmediate) {
        assertThat(application.isReadAccessAllowed).isTrue
      }
      assertThat(application.isReadAccessAllowed).isFalse
    }
  }


  @Test
  fun `edt coroutine cancellation happens under lock`() = timeoutRunBlocking {
    val job = Job()
    val computation = launch(Dispatchers.EDT) {
      try {
        suspendCancellableCoroutine {
          job.complete()
        }
      }
      finally {
        assertThat(application.isReadAccessAllowed).isTrue
        assertThat(application.isWriteIntentLockAcquired).isTrue
        assertThat(application.isWriteAccessAllowed).isFalse
      }
    }
    job.join()
    computation.cancelAndJoin()
  }

  @Test
  fun `edt coroutine holds lock`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertThat(application.isReadAccessAllowed).isTrue
      assertThat(application.isWriteIntentLockAcquired).isTrue
      assertThat(application.isWriteAccessAllowed).isFalse
    }
  }

  @Test
  fun `edt coroutine scheduled with ANY modality runs under lock`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      assertThat(application.isReadAccessAllowed).isTrue
    }
  }

  @Test
  fun `ui dispatcher does not have read access`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      assertFalse(application.isReadAccessAllowed)
      assertFalse(application.isWriteAccessAllowed)
      assertFalse(application.isWriteIntentLockAcquired)
      assertThrows<RuntimeException> {
        TransactionGuard.getInstance().isWritingAllowed
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `ui dispatcher cannot start locking actions`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      assertThrows<java.lang.IllegalStateException> {
        application.runWriteAction {
          fail()
        }
      }
      assertThrows<java.lang.IllegalStateException> {
        application.runReadAction {
          fail()
        }
      }
      assertThrows<java.lang.IllegalStateException> {
        ApplicationManagerEx.getApplicationEx().tryRunReadAction {
          fail()
        }
      }
      assertThrows<java.lang.IllegalStateException> {
        application.runWriteIntentReadAction<Unit, Exception> {
          fail()
        }
      }
    }
  }

  @Test
  fun `main dispatcher fails softly on locking actions`(): Unit = timeoutRunBlocking(context = Dispatchers.Main) {
    val counter = AtomicInteger()
    assertErrorLogged<ThreadingSupport.LockAccessDisallowed> {
      application.runWriteAction {
        counter.incrementAndGet()
      }
    }
    assertErrorLogged<ThreadingSupport.LockAccessDisallowed> {
      application.runReadAction {
        counter.incrementAndGet()
      }
    }
    assertErrorLogged<ThreadingSupport.LockAccessDisallowed> {
      assertTrue(ApplicationManagerEx.getApplicationEx().tryRunReadAction {
        counter.incrementAndGet()
      })
    }
    assertErrorLogged<ThreadingSupport.LockAccessDisallowed> {
      application.runWriteIntentReadAction<Unit, Exception> {
        counter.incrementAndGet()
      }
    }
    assertThat(counter.get()).isEqualTo(4)
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `ui dispatcher preserves modality`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertEquals(ModalityState.nonModal(), ModalityState.defaultModalityState())
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
      val currentModality = ModalityState.defaultModalityState()
      assertNotEquals(ModalityState.nonModal(), currentModality)
      withContext(Dispatchers.UI) {
        assertEquals(currentModality, ModalityState.defaultModalityState())
      }
    }
  }

  @Suppress("ForbiddenInSuspectContextMethod")
  @Test
  fun `ui dispatcher performs dispatch if thread holds lock`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertThat(application.isReadAccessAllowed).isTrue
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      assertThat(application.isReadAccessAllowed).isFalse
      assertThrows<java.lang.IllegalStateException> {
        application.runReadAction {
          fail()
        }
      }
    }
  }

  @Test
  fun `immediate main dispatcher proceeds when modality is non-trivial`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val job = Job()
    withModality {
      runBlocking(Dispatchers.Main.immediate) {
        job.complete()
      }
    }
    job.join()
  }

}

@RequiresEdt
// we cannot use `com.intellij.openapi.application.impl.UtilKt.withModality` because `Application.invokeAndWait` takes WIL
// and Dispatchers.UI is allergic to WIL
private fun withModality(action: () -> Unit) {
  val modalEntity = Any()
  LaterInvocator.enterModal(modalEntity)
  try {
    action()
  }
  finally {
    LaterInvocator.leaveModal(modalEntity)
  }
}


internal fun uiThreadDispatchers(): List<Arguments> = listOf(
  Dispatchers.EDT,
  Dispatchers.UI,
  Dispatchers.Main,
).map { Arguments.of(it) }

@Suppress("UnusedReceiverParameter")
val Dispatchers.UIImmediate: CoroutineDispatcher
  get() = (Dispatchers.UI[ContinuationInterceptor.Key] as MainCoroutineDispatcher).immediate

@Suppress("UnusedReceiverParameter")
val Dispatchers.EDTImmediate: CoroutineDispatcher
  get() = (Dispatchers.EDT[ContinuationInterceptor.Key] as MainCoroutineDispatcher).immediate