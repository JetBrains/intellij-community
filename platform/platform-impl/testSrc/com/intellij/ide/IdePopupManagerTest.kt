// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.ui.popup.IdePopupEventDispatcher
import com.intellij.openapi.ui.popup.JBPopup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.AWTEvent
import java.awt.Component
import java.util.stream.Stream

internal class IdePopupManagerTest {
  @Test
  fun `a dispatcher that an earlier close removed does not fail the result`() {
    val manager = IdePopupManager()
    val stacking = FakeDispatcher { false }
    lateinit var wizard: FakeDispatcher
    wizard = FakeDispatcher {
      manager.remove(wizard)
      manager.remove(stacking)
      true
    }
    manager.push(wizard)
    manager.push(stacking)

    assertTrue(manager.closeAllPopups(false))
    assertEquals(1, wizard.closeCount)
    assertEquals(0, stacking.closeCount)
  }

  @Test
  fun `a dispatcher that stays and does not close fails the result`() {
    val manager = IdePopupManager()
    manager.push(FakeDispatcher { true })
    manager.push(FakeDispatcher { false })

    assertFalse(manager.closeAllPopups(false))
  }
}

private class FakeDispatcher(private val onClose: () -> Boolean) : IdePopupEventDispatcher {
  var closeCount = 0

  override fun getComponent(): Component? = null

  override fun getPopupStream(): Stream<JBPopup> = Stream.empty()

  override fun dispatch(event: AWTEvent): Boolean = false

  override fun requestFocus(): Boolean = false

  override fun close(): Boolean {
    closeCount++
    return onClose()
  }
}
