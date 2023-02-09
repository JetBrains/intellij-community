// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.observable

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleEventDispatcherTest {

  @Test
  fun `test event dispatching`() {
    val dispatcher = SingleEventDispatcher.create()
    val eventCounter = AtomicInteger()

    Disposer.newDisposable().use { disposable ->
      dispatcher.whenEventHappened(disposable) {
        eventCounter.incrementAndGet()
      }
      assertEquals(0, eventCounter.get())
      dispatcher.fireEvent()
      assertEquals(1, eventCounter.get())
      dispatcher.fireEvent()
      assertEquals(2, eventCounter.get())
      dispatcher.fireEvent()
      assertEquals(3, eventCounter.get())
    }
    dispatcher.fireEvent()
    assertEquals(3, eventCounter.get())

    eventCounter.set(0)
    dispatcher.whenEventHappened {
      eventCounter.incrementAndGet()
    }
    assertEquals(0, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(1, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(2, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(3, eventCounter.get())
  }

  @Test
  fun `test event dispatching with ttl=1`() {
    val dispatcher = SingleEventDispatcher.create()
    val eventCounter = AtomicInteger()

    dispatcher.onceWhenEventHappened {
      eventCounter.incrementAndGet()
    }
    assertEquals(0, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(1, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(1, eventCounter.get())

    eventCounter.set(0)
    Disposer.newDisposable().use { disposable ->
      dispatcher.onceWhenEventHappened(disposable) {
        eventCounter.incrementAndGet()
      }
      assertEquals(0, eventCounter.get())
    }
    dispatcher.fireEvent()
    assertEquals(0, eventCounter.get())
  }

  @Test
  fun `test event dispatching with ttl=N`() {
    val dispatcher = SingleEventDispatcher.create()
    val eventCounter = AtomicInteger()

    dispatcher.whenEventHappened(10) {
      eventCounter.incrementAndGet()
    }
    repeat(10) {
      assertEquals(it, eventCounter.get())
      dispatcher.fireEvent()
      assertEquals(it + 1, eventCounter.get())
    }
    dispatcher.fireEvent()
    assertEquals(10, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(10, eventCounter.get())

    eventCounter.set(0)
    Disposer.newDisposable().use { disposable ->
      dispatcher.whenEventHappened(10, disposable) {
        eventCounter.incrementAndGet()
      }
      repeat(5) {
        assertEquals(it, eventCounter.get())
        dispatcher.fireEvent()
        assertEquals(it + 1, eventCounter.get())
      }
    }
    dispatcher.fireEvent()
    assertEquals(5, eventCounter.get())
    dispatcher.fireEvent()
    assertEquals(5, eventCounter.get())
  }

  @Test
  fun `test event handling during disposing`() {
    fun testDetach(serviceDisposable: CheckedDisposable, eventDisposable: Disposable, listenerDisposable: Disposable) {
      Disposer.newDisposable().use { disposable ->
        val dispatcher = SingleEventDispatcher.create()
        val eventCounter = AtomicInteger()
        dispatcher.whenEventHappened(disposable) {
          eventCounter.incrementAndGet()
        }
        Disposer.register(eventDisposable, Disposable {
          dispatcher.fireEvent()
        })
        dispatcher.onceWhenEventHappened(listenerDisposable) {
          @Suppress("DEPRECATION")
          Assertions.fail(
            "Listener shouldn't be called: " +
            "Disposable.isDisposed=${serviceDisposable.isDisposed}, " +
            "Disposer.isDisposed=${Disposer.isDisposed(serviceDisposable)}"
          )
        }
        assertEquals(0, eventCounter.get())
        Disposer.dispose(serviceDisposable)
        assertEquals(1, eventCounter.get())
      }
    }

    Disposer.newDisposable().use { disposable ->
      val serviceDisposable = Disposer.newCheckedDisposable(disposable)
      testDetach(serviceDisposable, serviceDisposable, serviceDisposable)
    }

    Disposer.newDisposable().use { disposable ->
      val serviceDisposable = Disposer.newCheckedDisposable(disposable)
      val parentDisposable = Disposer.newDisposable(serviceDisposable)
      testDetach(serviceDisposable, parentDisposable, parentDisposable)
    }

    Disposer.newDisposable().use { disposable ->
      val serviceDisposable = Disposer.newCheckedDisposable(disposable)
      val listenerDisposable = Disposer.newDisposable(serviceDisposable)
      testDetach(serviceDisposable, serviceDisposable, listenerDisposable)
    }

    Disposer.newDisposable().use { disposable ->
      val serviceDisposable = Disposer.newCheckedDisposable(disposable)
      val eventDisposable = Disposer.newDisposable(serviceDisposable)
      testDetach(serviceDisposable, eventDisposable, serviceDisposable)
    }

    Disposer.newDisposable().use { disposable ->
      val serviceDisposable = Disposer.newCheckedDisposable(disposable)
      val eventDisposable = Disposer.newDisposable(serviceDisposable)
      val listenerDisposable = Disposer.newDisposable(serviceDisposable)
      testDetach(serviceDisposable, eventDisposable, listenerDisposable)
    }
  }

  @Test
  fun `test filtered event dispatcher`() {
    val mainDispatcher = SingleEventDispatcher.create<String>()
    val parameter1Dispatcher = mainDispatcher.filterEvents { it == "parameter1" }
    val parameter2Dispatcher = mainDispatcher.filterEvents { it == "parameter2" }

    val mainEventCounter = AtomicInteger()
    val parameter1EventCounter = AtomicInteger()
    val parameter2EventCounter = AtomicInteger()

    val counters = listOf(mainEventCounter, parameter1EventCounter, parameter2EventCounter)

    Disposer.newDisposable().use { disposable ->
      mainEventCounter.set(0)
      parameter1EventCounter.set(0)
      parameter2EventCounter.set(0)

      mainDispatcher.whenEventHappened(disposable) {
        mainEventCounter.incrementAndGet()
      }
      parameter1Dispatcher.whenEventHappened(disposable) {
        assertEquals("parameter1", it)
        parameter1EventCounter.incrementAndGet()
      }
      parameter2Dispatcher.whenEventHappened(disposable) {
        assertEquals("parameter2", it)
        parameter2EventCounter.incrementAndGet()
      }

      assertEquals(listOf(0, 0, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter")
      assertEquals(listOf(1, 0, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter1")
      assertEquals(listOf(2, 1, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter2")
      assertEquals(listOf(3, 1, 1), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter1")
      assertEquals(listOf(4, 2, 1), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter2")
      assertEquals(listOf(5, 2, 2), counters.map { it.get() })
    }

    Disposer.newDisposable().use { disposable ->
      mainEventCounter.set(0)
      parameter1EventCounter.set(0)
      parameter2EventCounter.set(0)

      mainDispatcher.whenEventHappened(disposable) {
        mainEventCounter.incrementAndGet()
      }
      parameter1Dispatcher.onceWhenEventHappened(disposable) {
        assertEquals("parameter1", it)
        parameter1EventCounter.incrementAndGet()
      }
      parameter2Dispatcher.onceWhenEventHappened(disposable) {
        assertEquals("parameter2", it)
        parameter2EventCounter.incrementAndGet()
      }

      assertEquals(listOf(0, 0, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter")
      assertEquals(listOf(1, 0, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter1")
      assertEquals(listOf(2, 1, 0), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter2")
      assertEquals(listOf(3, 1, 1), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter1")
      assertEquals(listOf(4, 1, 1), counters.map { it.get() })
      mainDispatcher.fireEvent("parameter2")
      assertEquals(listOf(5, 1, 1), counters.map { it.get() })
    }
  }
}