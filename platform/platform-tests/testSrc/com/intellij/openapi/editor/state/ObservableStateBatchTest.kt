// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * A batch gives its events after it ends, so each listener sees all the values that the batch changes.
 */
class ObservableStateBatchTest {
  private class TestState : ObservableState() {
    var width: Int by property(1)
    var height: Int by property(1)
  }

  /**
   * A state whose two properties both read [source].
   */
  private class SourceState(private val source: () -> Int) : ObservableState() {
    val first: Int by property { source() }
    val second: Int by property { source() }
  }

  private val events = mutableListOf<String>()

  @Test
  fun testABatchGivesItsEventsAfterAllItsChanges() {
    val state = TestState().recordEvents()

    state.batch {
      state.width = 2
      state.height = 3
      assertEquals(emptyList<String>(), events)
    }

    assertEquals(listOf("width 2x3", "height 2x3"), events)
  }

  @Test
  fun testANestedBatchGivesItsEventsAtTheEndOfTheOuterBatch() {
    val state = TestState().recordEvents()

    state.batch {
      state.batch {
        state.width = 2
      }
      assertEquals(emptyList<String>(), events)
      state.height = 3
    }

    assertEquals(listOf("width 2x3", "height 2x3"), events)
  }

  @Test
  fun testAFailedBatchStillGivesItsEvents() {
    val state = TestState().recordEvents()

    assertThrows(IllegalStateException::class.java) {
      state.batch {
        state.width = 2
        throw IllegalStateException("the batch fails")
      }
    }

    assertEquals(listOf("width 2x1"), events)
  }

  /**
   * A first override changes the value and the override, so it counts twice. The count grows before the batch ends.
   */
  @Test
  fun testEachChangeCountsAtOnce() {
    val state = TestState()
    val before = state.getModificationCount()

    state.batch {
      state.width = 2
      assertEquals(before + 2, state.getModificationCount())
    }
    state.clearOverriding(state::width)

    assertEquals(before + 4, state.getModificationCount())
  }

  @Test
  fun testRefreshAllIsOneBatch() {
    var source = 1
    val state = SourceState { source }
    state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        events.add("${event.propertyName} ${state.first}x${state.second}")
      }
    })

    source = 2
    state.refreshAll()

    assertEquals(listOf("first 2x2", "second 2x2"), events)
  }

  /**
   * Records each value event with the values of both properties when the event comes.
   */
  private fun TestState.recordEvents(): TestState {
    addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        events.add("${event.propertyName} ${width}x${height}")
      }
    })
    return this
  }
}
