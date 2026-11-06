// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.util.ConcurrencyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The values of a state are in one immutable snapshot. A batch publishes its changes in one new snapshot, before the
 * events, and another thread never sees a part of a batch.
 */
class ObservableStateSnapshotTest {
  private class TestState : ObservableState() {
    var width: Int by property(1)
    var height: Int by property(1)
  }

  /**
   * A state whose [doubled] default reads [width].
   */
  private class DerivedState : ObservableState() {
    var width: Int by property(1)
    val doubled: Int by property(0) { width * 2 }
  }

  /**
   * A state that reads a property while it is built, before its second property registers.
   */
  private class EarlyReadState : ObservableState() {
    val first: Int by property(1)

    @Suppress("unused")
    val copyOfFirst: Int = first

    @Suppress("unused")
    val second: Int by property(2)
  }

  @Test
  fun testASnapshotKeepsItsValues() {
    val state = TestState()
    val width = widthOf(state)
    val before = state.getSnapshot()

    state.width = 2
    val after = state.getSnapshot()

    assertEquals(1, before.get(width))
    assertFalse(before.isOverridden(width))
    assertEquals(2, after.get(width))
    assertTrue(after.isOverridden(width))
  }

  @Test
  fun testAnUnchangedOverrideKeepsTheSnapshot() {
    val state = TestState()
    state.width = 2
    val before = state.getSnapshot()

    state.width = 2

    assertSame(before, state.getSnapshot())
  }

  @Test
  fun testAListenerReadsTheNewSnapshot() {
    val state = TestState()
    val width = widthOf(state)
    val seen = mutableListOf<Any>()
    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        seen.add(state.getSnapshot().get(width))
      }
    })

    state.width = 2

    assertEquals(listOf(2), seen)
  }

  /**
   * The thread of the batch reads the changes of the batch, so a default that reads another property sees its new value.
   */
  @Test
  fun testTheBatchThreadReadsTheChangesOfTheBatch() {
    val state = DerivedState().apply {
      refreshAll()
    }

    state.batch {
      state.width = 5
      assertEquals(5, state.width)
      state.refresh(state::doubled)
      assertEquals(10, state.doubled)
    }

    assertEquals(10, state.doubled)
  }

  /**
   * A read on another thread takes no lock, so it does not wait for the batch, and it sees the values before the batch.
   */
  @Test
  fun testAnotherThreadSeesNoChangeOfAnOpenBatch() {
    val state = TestState()

    state.batch {
      state.width = 2
      state.height = 3
      assertEquals("1x1", runOnAnotherThread { "${state.width}x${state.height}" })
    }

    assertEquals("2x3", runOnAnotherThread { "${state.width}x${state.height}" })
  }

  @Test
  fun testAReaderNeverSeesAPartOfABatch() {
    val state = TestState()
    val width = widthOf(state)
    val height = heightOf(state)

    val writers = runOnTwoThreads { writer ->
      for (i in 1..WRITES) {
        state.batch {
          state.width = writer * WRITES + i
          state.height = writer * WRITES + i
        }
      }
    }
    while (!writers.isDone) {
      val current = state.getSnapshot()
      assertEquals(current.get(width), current.get(height))
    }

    writers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  /**
   * Each batch reads the value and writes it back plus one. A batch that runs at the same time as another one loses an
   * increment.
   */
  @Test
  fun testIncrementsOnTwoThreadsAllStay() {
    val state = TestState()

    runOnTwoThreads {
      repeat(WRITES) {
        state.batch {
          state.width += 1
        }
      }
    }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    assertEquals(1 + 2 * WRITES, state.width)
  }

  /**
   * A listener runs without the lock, so it can wait for a write on another thread.
   */
  @Test
  fun testAListenerCanWaitForAWriteOnAnotherThread() {
    val state = TestState()
    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        if (event.propertyName == "width") {
          runOnAnotherThread { state.height = 5 }
        }
      }
    })

    state.width = 2

    assertEquals(5, state.height)
  }

  @Test
  fun testAPropertyAfterTheFirstReadFails() {
    assertThrows(IllegalStateException::class.java) {
      EarlyReadState()
    }
  }

  private fun widthOf(state: ObservableState): StateProperty<Any> {
    return state.findProperty("width")!!
  }

  private fun heightOf(state: ObservableState): StateProperty<Any> {
    return state.findProperty("height")!!
  }

  private fun <T> runOnAnotherThread(action: () -> T): T {
    val executor = ConcurrencyUtil.newSingleThreadExecutor("ObservableStateSnapshotTest")
    try {
      return CompletableFuture.supplyAsync(action, executor).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } finally {
      executor.shutdown()
    }
  }

  /**
   * Runs [write] on two new threads at the same time, with the writer number 0 and 1. The future fails when a writer fails.
   */
  private fun runOnTwoThreads(write: (Int) -> Unit): CompletableFuture<Void> {
    val executor = Executors.newFixedThreadPool(2, ConcurrencyUtil.newNamedThreadFactory("ObservableStateSnapshotTest writer"))
    val start = CountDownLatch(1)
    val writers = List(2) { writer ->
      CompletableFuture.runAsync({
        start.await()
        write(writer)
      }, executor)
    }
    start.countDown()
    return CompletableFuture.allOf(*writers.toTypedArray()).whenComplete { _, _ -> executor.shutdown() }
  }

  private companion object {
    const val WRITES: Int = 2000
    const val TIMEOUT_SECONDS: Long = 30
  }
}
