// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The first snapshot freezes the properties of a state. All states of one class have the same properties.
 */
class ObservableStateLayoutTest {
  /**
   * A state whose [extra] instances declare one more property than the others.
   */
  private class VaryingState(extra: Boolean) : ObservableState() {
    @Suppress("unused")
    var width: Int by property(1)

    init {
      if (extra) {
        property(2)
      }
    }
  }

  /**
   * A state with a plain field next to its property.
   */
  private class PlainFieldState : ObservableState() {
    var width: Int by property(1)
    var plain: Int = 0
  }

  @Test
  fun testAStateWithOtherPropertiesThanItsClassFails() {
    VaryingState(extra = false).getSnapshot()

    assertThrows(IllegalStateException::class.java) {
      VaryingState(extra = true).getSnapshot()
    }
  }

  @Test
  fun testAPlainFieldIsNoProperty() {
    val state = PlainFieldState()

    assertNull(state.findProperty("plain"))
    assertThrows(IllegalArgumentException::class.java) {
      state.refresh(state::plain)
    }
    assertThrows(IllegalArgumentException::class.java) {
      state.clearOverriding(state::plain)
    }
  }

  @Test
  fun testTheStateFindsItsPropertiesAfterTheFreeze() {
    val state = PlainFieldState()
    state.width = 2

    assertEquals(listOf("width"), state.__getProperties().map { it.name })
    assertEquals(2, state.findProperty("width")?.getValue(state))
  }
}
