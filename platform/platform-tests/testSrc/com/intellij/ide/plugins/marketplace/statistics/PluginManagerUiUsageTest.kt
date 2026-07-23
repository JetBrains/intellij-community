// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerFUSCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerButtonType
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerManageAction
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerSide
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerTab
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginManagerUiUsageTest {

  @Test
  fun `tab selected logs each tab`(@TestDisposable disposable: Disposable) {
    for (tab in PluginManagerTab.entries) {
      val iterationDisposable = Disposer.newDisposable(disposable, "PluginManagerUiUsageTest.tab.$tab")
      try {
        val events = FUCollectorTestCase.collectLogEvents(iterationDisposable) {
          PluginManagerUsageCollector.tabSelected(tab)
        }
        val event = events.single { it.event.id == "tab.selected" }
        assertEquals(tab.toString(), event.event.data["tab"])
        assertNotNull(event.event.data["sessionId"])
        assertNotNull(event.event.data["searchSessionId"])
      }
      finally {
        Disposer.dispose(iterationDisposable)
      }
    }
  }

  @Test
  fun `manage action invoked logs each action`(@TestDisposable disposable: Disposable) {
    for (action in PluginManagerManageAction.entries) {
      val iterationDisposable = Disposer.newDisposable(disposable, "PluginManagerUiUsageTest.action.$action")
      try {
        val events = FUCollectorTestCase.collectLogEvents(iterationDisposable) {
          PluginManagerUsageCollector.manageActionInvoked(action)
        }
        val event = events.single { it.event.id == "manage.action.invoked" }
        assertEquals(action.toString(), event.event.data["action"])
        assertNotNull(event.event.data["sessionId"])
        assertNotNull(event.event.data["searchSessionId"])
      }
      finally {
        Disposer.dispose(iterationDisposable)
      }
    }
  }

  @Test
  fun `new search journey keeps plugin manager session id`(@TestDisposable disposable: Disposable) {
    val descriptor = requireNotNull(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID))
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PluginManagerUsageCollector.logSessionStarted()
      PluginManagerUsageCollector.pluginInstallationFinished(descriptor)
      PluginManagerUsageCollector.updateAndGetSearchIndex()
      PluginManagerUsageCollector.tabSelected(PluginManagerTab.INSTALLED)
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.MANAGE_REPOSITORIES)
      PluginManagerUsageCollector.pluginInstallationFinished(descriptor)
      PluginManagerUsageCollector.updateAndGetSearchIndex()
    }

    val sessionStarted = events.single { it.event.id == "session.started" }
    val installations = events.filter { it.event.id == "plugin.installation.finished" }
    assertEquals(2, installations.size)
    assertEquals(sessionStarted.event.data["sessionId"], installations[0].event.data["sessionId"])
    assertEquals(sessionStarted.event.data["sessionId"], installations[1].event.data["sessionId"])
    assertEquals(sessionStarted.event.data["searchSessionId"], installations[0].event.data["searchSessionId"])
    assertNotEquals(installations[0].event.data["searchSessionId"], installations[1].event.data["searchSessionId"])
    for (eventId in listOf("tab.selected", "manage.action.invoked")) {
      val event = events.single { it.event.id == eventId }
      assertEquals(sessionStarted.event.data["sessionId"], event.event.data["sessionId"])
      assertEquals(installations[1].event.data["searchSessionId"], event.event.data["searchSessionId"])
    }
  }

  @Test
  fun `session activity uses session started id`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PluginManagerUsageCollector.logSessionStarted()
      PluginManagerUsageCollector.tabSelected(PluginManagerTab.INSTALLED)
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.MANAGE_REPOSITORIES)
    }

    val startedSessionId = events.single { it.event.id == "session.started" }.event.data["sessionId"]
    assertNotNull(startedSessionId)
    for (eventId in listOf("tab.selected", "manage.action.invoked")) {
      assertEquals(startedSessionId, events.single { it.event.id == eventId }.event.data["sessionId"])
    }
  }

  @Test
  fun `plugin install and uninstall button clicks report side and button type`(@TestDisposable disposable: Disposable) {
    val descriptor = requireNotNull(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID))
    val collector = PluginManagerFUSCollector()

    for (side in PluginManagerSide.entries) {
      for (buttonType in PluginManagerButtonType.entries) {
        val iterationDisposable = Disposer.newDisposable(disposable, "PluginManagerUiUsageTest.$side.$buttonType")
        try {
          val events = FUCollectorTestCase.collectLogEvents(iterationDisposable) {
            collector.pluginInstallButtonClicked(descriptor.pluginId, side, buttonType, sessionId = 1, searchSessionId = 2)
            collector.pluginUninstallButtonClicked(descriptor.pluginId, side, buttonType, sessionId = 1, searchSessionId = 2)
          }

          for (eventId in listOf("plugin.install.button.clicked", "plugin.uninstall.button.clicked")) {
            val event = events.single { it.event.id == eventId }
            assertEquals(side.toString(), event.event.data["side"])
            assertEquals(buttonType.toString(), event.event.data["button_type"])
            assertEquals(1, event.event.data["sessionId"])
            assertEquals(2, event.event.data["searchSessionId"])
          }
        }
        finally {
          Disposer.dispose(iterationDisposable)
        }
      }
    }
  }
}
