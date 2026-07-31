// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerConfigurableTreeRenderer
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.ui.LafManager
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.treeStructure.SimpleTree
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.swing.event.AncestorEvent

@TestApplication
internal class PluginManagerOpenSourceTest {

  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      // Workaround to avoid 'no ComponentUI class' error for JB components, like JBButton and such
      LafManager.getInstance()
    }
  }

  @Test
  fun `session started logs source for each value`(@TestDisposable disposable: Disposable) {
    for (source in PluginManagerOpenSourceEnum.entries) {
      val iterationDisposable = Disposer.newDisposable(disposable, "PluginManagerOpenSourceTest.$source")
      try {
        val events = FUCollectorTestCase.collectLogEvents(iterationDisposable) {
          PluginManagerUsageCollector.logSessionStarted(source)
        }
        val sessionStartedEvent = events.single { it.event.id == "session.started" }
        assertEquals(source.toString(), sessionStartedEvent.event.data["source"])
        assertNotNull(sessionStartedEvent.event.data["sessionId"])
        assertNotNull(sessionStartedEvent.event.data["searchSessionId"])
      }
      finally {
        Disposer.dispose(iterationDisposable)
      }
    }
  }

  @Test
  fun `default source is other`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PluginManagerUsageCollector.logSessionStarted()
    }
    val sessionStartedEvent = events.single { it.event.id == "session.started" }
    assertEquals(PluginManagerOpenSourceEnum.OTHER.toString(), sessionStartedEvent.event.data["source"])
  }

  @Test
  fun `fromActionPlace maps known and default places`() {
    assertEquals(PluginManagerOpenSourceEnum.ACTION_SEARCH, PluginManagerOpenSourceEnum.fromActionPlace(ActionPlaces.ACTION_SEARCH))
    assertEquals(PluginManagerOpenSourceEnum.NOTIFICATION, PluginManagerOpenSourceEnum.fromActionPlace(ActionPlaces.NOTIFICATION))
    assertEquals(PluginManagerOpenSourceEnum.WELCOME_SCREEN, PluginManagerOpenSourceEnum.fromActionPlace(ActionPlaces.WELCOME_SCREEN))
    assertEquals(PluginManagerOpenSourceEnum.TOOLBAR, PluginManagerOpenSourceEnum.fromActionPlace(ActionPlaces.MAIN_MENU))
    assertEquals(PluginManagerOpenSourceEnum.TOOLBAR, PluginManagerOpenSourceEnum.fromActionPlace(null))
  }

  @Test
  fun `welcome screen factory is threaded into session started`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable.createForWelcomeScreen()
      try {
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          configurable.createComponent()
        }
        val sessionStartedEvent = events.single { it.event.id == "session.started" }
        assertEquals(PluginManagerOpenSourceEnum.WELCOME_SCREEN.toString(), sessionStartedEvent.event.data["source"])
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  fun `explicit open source is not overwritten by settings`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      configurable.setOpenSource(PluginManagerOpenSourceEnum.NOTIFICATION)
      configurable.setOpenSourceFromSettings()
      try {
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          configurable.createComponent()
        }
        val sessionStartedEvent = events.single { it.event.id == "session.started" }
        assertEquals(PluginManagerOpenSourceEnum.NOTIFICATION.toString(), sessionStartedEvent.event.data["source"])
      }
      finally {
        configurable.disposeUIResources()
      }
    }

  @Test
  fun `settings tree renderer is threaded into session started`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      val renderer = PluginManagerConfigurableTreeRenderer()
      val tree = SimpleTree()
      try {
        renderer.getDecorator(tree, configurable, true)
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          configurable.createComponent()
        }
        val sessionStartedEvent = events.single { it.event.id == "session.started" }
        assertEquals(PluginManagerOpenSourceEnum.SETTINGS.toString(), sessionStartedEvent.event.data["source"])
      }
      finally {
        configurable.disposeUIResources()
        renderer.ancestorRemoved(AncestorEvent(tree, AncestorEvent.ANCESTOR_REMOVED, null, null))
      }
    }

  @Test
  fun `createComponent without setOpenSource logs default other`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val configurable = PluginManagerConfigurable()
      try {
        val events = FUCollectorTestCase.collectLogEvents(disposable) {
          configurable.createComponent()
        }
        val sessionStartedEvent = events.single { it.event.id == "session.started" }
        assertEquals(PluginManagerOpenSourceEnum.OTHER.toString(), sessionStartedEvent.event.data["source"])
      }
      finally {
        configurable.disposeUIResources()
      }
    }
}
