// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ide.plugins.newui.TabbedPaneHeaderComponent
import com.intellij.ide.ui.LafManager
import com.intellij.util.progress.sleepCancellable
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent

@TestApplication
internal class PluginManagerConfigurablePanelTabSelectionTest {

  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      // Workaround to avoid 'no ComponentUI class' error for JB components, like JBButton and such
      LafManager.getInstance()
    }
  }

  @Test
  fun `programmatic tab navigation does not log tab selected`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val panel = PluginManagerConfigurablePanel(null)
      try {
        panel.openMarketplaceTab("") // normalize starting tab regardless of persisted selection state
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          panel.openInstalledTab("")
        }
        assertTrue(events.none { it.event.id == "tab.selected" }, "programmatic navigation must not log tab.selected: $events")
      }
      finally {
        Disposer.dispose(panel)
      }
    }

  @Test
  fun `real tab header selection change logs tab selected`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val panel = PluginManagerConfigurablePanel(null)
      try {
        panel.openMarketplaceTab("") // normalize starting tab
        val tabbedPane = getTabbedPane(panel)
        tabbedPane.setBounds(0, 0, 800, 40)
        tabbedPane.doLayout()
        assertEquals(0, tabbedPane.selectedIndex)
        val installedTabBounds = tabbedPane.getBoundsAt(1)
        assertNotNull(installedTabBounds,
                      "Installed tab has no layout bounds after doLayout(): tabCount=${tabbedPane.tabCount}, size=${tabbedPane.size}")
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          tabbedPane.dispatchEvent(
            MouseEvent(
              tabbedPane, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
              installedTabBounds.centerX.toInt(), installedTabBounds.centerY.toInt(), 1, false, MouseEvent.BUTTON1,
            ),
          )
        }
        val event = events.single { it.event.id == "tab.selected" }
        assertEquals("INSTALLED", event.event.data["tab"])
        assertEquals(1, tabbedPane.selectedIndex)
      }
      finally {
        Disposer.dispose(panel)
      }
    }

  @Test
  fun `programmatic setSelection does not log tab selected`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val panel = PluginManagerConfigurablePanel(null)
      try {
        panel.openMarketplaceTab("")
        val tabbedPane = getTabbedPane(panel)
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          // A direct, non-user-driven selection change (as opposed to going through
          // PluginManagerConfigurablePanel's own methods) must not log either.
          tabbedPane.selectedIndex = 1
        }
        assertTrue(events.none { it.event.id == "tab.selected" }, "direct setSelectedIndex must not log tab.selected: $events")
      }
      finally {
        Disposer.dispose(panel)
      }
    }

  private fun getTabbedPane(panel: PluginManagerConfigurablePanel): JBTabbedPane {
    val tabHeaderComponent = panel.getTopComponent()
    val field = TabbedPaneHeaderComponent::class.java.getDeclaredField("myTabbedPane")
    field.isAccessible = true
    return field.get(tabHeaderComponent) as JBTabbedPane
  }
}
