// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class EdtCoroutineDispatcherTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  @Test
  fun `dispatch thread`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ThreadingAssertions.assertEventDispatchThread()
    }
  }

  @Test
  fun `externally cancelled coroutine`(): Unit = timeoutRunBlocking {
    val edtJob = launch(Dispatchers.EDT) {
      delay(Long.MAX_VALUE)
    }
    edtJob.cancel()
  }

  @Test
  fun `internally cancelled coroutine`(): Unit = timeoutRunBlocking {
    assertThrows<CancellationException> {
      withContext(Job() + Dispatchers.EDT) {
        throw CancellationException()
      }
    }
  }

  @Test
  fun `failed coroutine`(): Unit = timeoutRunBlocking {
    val t = object : Throwable() {}
    val thrown = assertThrows<Throwable> {
      withContext(Job() + Dispatchers.EDT) {
        throw t
      }
    }
    //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(t, thrown)
  }

  @Test
  fun `cancelled coroutine finishes normally ignoring modality and does not leak`() {
    val leak = object : Any() {
      override fun toString(): String = "leak"
    }
    val root = LaterInvocator::class.java

    ApplicationManager.getApplication().withModality {
      @OptIn(DelicateCoroutinesApi::class)
      val job = GlobalScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
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

  @Test
  fun `switch to EDT under read lock fails with ISE`(): Unit = timeoutRunBlocking {
    readAction {
      assertThrows<IllegalStateException> {
        runBlockingMaybeCancellable {
          launch(Dispatchers.Default) {
            withContext(Dispatchers.EDT) {
              fail<Nothing>()
            }
          }
        }
      }
    }
  }

  @Test
  fun `immediate dispatch under null modality`(): Unit = `immediate dispatch`(null, null)

  @Test
  fun `immediate dispatch explicitly nonModal`(): Unit = `immediate dispatch`(ModalityState.nonModal(), ModalityState.nonModal())

  @Test
  fun `explicit nonModal is immediately dispatched under null modality`(): Unit = `immediate dispatch`(null, ModalityState.nonModal())

  @Test
  fun `null modality is immediately dispatched under explicit nonModal`(): Unit = `immediate dispatch`(ModalityState.nonModal(), null)

  private fun `immediate dispatch`(outerModality: ModalityState?, innerModality: ModalityState?): Unit = timeoutRunBlocking {
    val eventNum = AtomicInteger()
    val events = hashSetOf<Int>()
    withContext(Dispatchers.EDT.let { outerModality?.asContextElement()?.plus(it) ?: it }) {
      val job1 = launch(Dispatchers.Main.immediate.let { innerModality?.asContextElement()?.plus(it) ?: it }) {
        events += eventNum.get()
      }
      val job2 = launch(Dispatchers.EDT.let { innerModality?.asContextElement()?.plus(it) ?: it }) {
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

  @Test
  fun `immediate dispatch with the same modality`(): Unit = timeoutRunBlocking {
    val eventNum = AtomicInteger()
    val events = hashSetOf<Int>()
    val jobs = mutableListOf<Job>()
    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().withModality {
        jobs += launch(Dispatchers.Main.immediate + ModalityState.current().asContextElement()) {
          events += eventNum.get()
        }
        jobs += launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
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

  @Test
  fun `immediate dispatch is not performed when the context modality is lower`(): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(Dispatchers.EDT) {
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
      ApplicationManager.getApplication().withModality {
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

  @Test
  fun `immediate dispatch is performed when the context modality is any`(): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(Dispatchers.EDT) {
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
      ApplicationManager.getApplication().withModality {
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

  @Test
  fun `immediate dispatch is not performed when the context modality is null`(): Unit = timeoutRunBlocking {
    val jobs = mutableListOf<Job>()
    withContext(Dispatchers.EDT) {
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
      ApplicationManager.getApplication().withModality {
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
}
