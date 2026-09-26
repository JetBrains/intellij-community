// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import javax.swing.JComponent

internal class FocusUtilTest {
  @Test
  fun `a subscription follows a replaced focus manager`() {
    withFocusManagerRestored { parent, seen ->
      FocusUtil.addFocusOwnerListener(parent, PropertyChangeListener { seen.add(it.newValue as Component?) })

      val first = TestComponent()
      KeyboardFocusManager.setCurrentKeyboardFocusManager(TestFocusManager(first))
      assertThat(seen).containsExactly(first)

      // The second replacement only reaches the subscription through the manager it moved to.
      val second = TestComponent()
      KeyboardFocusManager.setCurrentKeyboardFocusManager(TestFocusManager(second))
      assertThat(seen).containsExactly(first, second)
    }
  }

  @Test
  fun `a replacement by the same manager reports nothing`() {
    withFocusManagerRestored { parent, seen ->
      FocusUtil.addFocusOwnerListener(parent, PropertyChangeListener { seen.add(it.newValue as Component?) })

      val manager = TestFocusManager(TestComponent())
      KeyboardFocusManager.setCurrentKeyboardFocusManager(manager)
      seen.clear()

      KeyboardFocusManager.setCurrentKeyboardFocusManager(manager)
      assertThat(seen).isEmpty()
    }
  }

  @Test
  fun `a disposed subscription hears nothing`() {
    withFocusManagerRestored { parent, seen ->
      FocusUtil.addFocusOwnerListener(parent, PropertyChangeListener { seen.add(it.newValue as Component?) })
      Disposer.dispose(parent)

      KeyboardFocusManager.setCurrentKeyboardFocusManager(TestFocusManager(TestComponent()))
      assertThat(seen).isEmpty()
    }
  }

  private fun withFocusManagerRestored(test: (parent: Disposable, seen: MutableList<Component?>) -> Unit) {
    val original = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val parent = Disposer.newDisposable()
    try {
      test(parent, mutableListOf())
    }
    finally {
      Disposer.dispose(parent)
      KeyboardFocusManager.setCurrentKeyboardFocusManager(original)
    }
  }

  private class TestComponent : JComponent()

  private class TestFocusManager(private val owner: Component) : DefaultKeyboardFocusManager() {
    override fun getFocusOwner(): Component = owner
  }
}
