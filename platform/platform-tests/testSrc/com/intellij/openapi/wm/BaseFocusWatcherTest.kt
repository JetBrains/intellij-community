// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.FocusEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
class BaseFocusWatcherTest {
  @Test
  fun `deinstall tolerates container mutations from componentUnregistered`() {
    SwingUtilities.invokeAndWait {
      runDeinstallWithMutatingUnregisterCallback()
    }
  }

  private fun runDeinstallWithMutatingUnregisterCallback() {
    val parent = JPanel()
    val firstChild = JPanel()
    val secondChild = JPanel()
    parent.add(firstChild)
    parent.add(secondChild)

    val watcher = object : BaseFocusWatcher() {
      override fun focusGained(e: FocusEvent?) = Unit

      override fun focusLost(e: FocusEvent?) = Unit

      override fun componentUnregistered(component: Component, cause: AWTEvent?) {
        if (component === firstChild && secondChild.parent === parent) {
          parent.remove(secondChild)
        }
      }
    }

    watcher.install(parent)
    watcher.deinstall(parent)
  }
}
