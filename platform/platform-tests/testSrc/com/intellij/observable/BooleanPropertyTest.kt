// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BooleanPropertyTest {

  @Test
  fun `test parallel setup`() {
    repeat(1000) {
      val setEventCounter = AtomicInteger(0)
      val resetEventCounter = AtomicInteger(0)
      val changeEventCounter = AtomicInteger(0)
      val observable = AtomicBooleanProperty(false)
      observable.afterSet { setEventCounter.incrementAndGet() }
      observable.afterReset { resetEventCounter.incrementAndGet() }
      observable.afterChange { changeEventCounter.incrementAndGet() }

      runConcurrentAction {
        observable.set()
      }
      assertEquals(true, observable.get())
      assertEquals(1, setEventCounter.get())
      assertEquals(0, resetEventCounter.get())
      assertEquals(10, changeEventCounter.get())

      runConcurrentAction {
        observable.reset()
      }
      assertEquals(false, observable.get())
      assertEquals(1, setEventCounter.get())
      assertEquals(1, resetEventCounter.get())
      assertEquals(20, changeEventCounter.get())

      runConcurrentAction {
        observable.compareAndSet(false, true)
      }
      assertEquals(true, observable.get())
      assertEquals(2, setEventCounter.get())
      assertEquals(1, resetEventCounter.get())
      assertEquals(21, changeEventCounter.get())

      runConcurrentAction {
        observable.compareAndSet(true, false)
      }
      assertEquals(false, observable.get())
      assertEquals(2, setEventCounter.get())
      assertEquals(2, resetEventCounter.get())
      assertEquals(22, changeEventCounter.get())

      runConcurrentAction {
        observable.compareAndSet(true, false)
      }
      assertEquals(false, observable.get())
      assertEquals(2, setEventCounter.get())
      assertEquals(2, resetEventCounter.get())
      assertEquals(22, changeEventCounter.get())

      runConcurrentAction {
        observable.compareAndSet(false, false)
      }
      assertEquals(false, observable.get())
      assertEquals(2, setEventCounter.get())
      assertEquals(2, resetEventCounter.get())
      assertEquals(32, changeEventCounter.get())
    }
  }

  private fun <R> generate(times: Int, action: (Int) -> R): Iterable<R> {
    return (0 until times).map(action)
  }

  private fun runConcurrentAction(action: () -> Unit) {
    val latch = CountDownLatch(1)
    val threads = generate(10) {
      thread {
        latch.await()
        action()
      }
    }
    latch.countDown()
    threads.forEach(Thread::join)
  }
}