// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A change of the override notifies the listeners also when the value stays the same. The Remote Development backend
 * needs that event to send an override to the frontend, or to remove it there.
 */
class ObservableStateOverridingTest {
  private class TestState : ObservableState() {
    var size: Int by property(1)
  }

  private val events = mutableListOf<String>()

  @Test
  fun testAnOverrideWithTheSameValueNotifiesTheOverridingOnly() {
    val state = TestState().recordEvents()

    state.size = 1

    assertEquals(listOf("overriding size"), events)
  }

  @Test
  fun testARemovedOverrideWithTheSameValueNotifiesTheOverridingOnly() {
    val state = TestState()
    state.size = 1
    state.recordEvents()

    state.clearOverriding(state::size)

    assertEquals(listOf("overriding size"), events)
  }

  @Test
  fun testAChangedValueNotifiesTheValueAndThenTheOverriding() {
    val state = TestState().recordEvents()

    state.size = 2
    state.size = 3
    state.clearOverriding(state::size)

    assertEquals(
      listOf("changed size", "overriding size", "changed size", "changed size", "overriding size"),
      events,
    )
  }

  private fun TestState.recordEvents(): TestState {
    addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        events.add("changed ${event.propertyName}")
      }

      override fun overridingChanged(state: ObservableState, propertyName: String) {
        events.add("overriding $propertyName")
      }
    })
    return this
  }
}
