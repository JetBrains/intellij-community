// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class ToolWindowStripeButtonTest {
  companion object {
    private const val ID = "stripe-test"
    private val projectFixture = projectFixture()
  }

  @Test
  fun `host layout and availability do not replace the plugin stripe default`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    for (bundled in listOf(true, false)) {
      val pluginDefault = !bundled
      for (savedChoice in listOf(null, !pluginDefault)) {
        withWindow(available = false, bundled = bundled, savedChoice = savedChoice) { _, window ->
          assertThat(window.stripeButtonVisibilityDefault).isEqualTo(pluginDefault)

          window.isAvailable = true

          assertThat(window.stripeButtonVisibilityDefault).isEqualTo(pluginDefault)
        }
      }
    }
  }

  @Test
  fun `the factory stripe choice overrides the plugin default even with a saved host layout`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      for (factoryVisibilityChoice in listOf(true, false)) {
        // Both the plugin default and the saved layout oppose the factory choice.
        withWindow(
          bundled = factoryVisibilityChoice,
          savedChoice = !factoryVisibilityChoice,
          factoryVisibilityChoice = factoryVisibilityChoice,
        ) { _, window ->
          assertThat(window.stripeButtonVisibilityDefault).isEqualTo(factoryVisibilityChoice)
        }
      }
    }

  @Test
  fun `a later explicit stripe choice is retained for lazy publication`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withWindow(savedChoice = true, factoryVisibilityChoice = true) { manager, window ->
      assertThat(window.stripeButtonVisibilityDefault).isTrue()
      assertThat(manager.isStripeButtonShow(window)).isTrue()

      window.isShowStripeButton = false

      assertThat(window.stripeButtonVisibilityDefault).isFalse()
      // New UI keeps the host layout. The recorded choice can seed a later frontend registration.
      assertThat(window.isShowStripeButton).isTrue()
      assertThat(manager.isStripeButtonShow(window)).isTrue()
    }
  }

  private suspend fun withWindow(
    available: Boolean = true,
    bundled: Boolean = true,
    savedChoice: Boolean? = null,
    factoryVisibilityChoice: Boolean? = null,
    check: (ToolWindowManagerImpl, ToolWindowImpl) -> Unit,
  ): Unit = coroutineScope {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = object : ToolWindowManagerImpl(projectFixture.get(), isNewUi = true, isEdtRequired = false, this@coroutineScope) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
    }
    val infos = HashMap<String, WindowInfoImpl>()
    if (savedChoice != null) {
      infos[ID] = WindowInfoImpl().apply {
        id = ID
        isShowStripeButton = savedChoice
      }
    }
    manager.setLayoutOnInit(DesktopLayout(infos))
    try {
      val factory = object : ToolWindowFactory {
        override fun init(toolWindow: ToolWindow) {
          factoryVisibilityChoice?.let { toolWindow.isShowStripeButton = it }
        }

        override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) = Unit
      }
      val window = manager.registerToolWindow(
        RegisterToolWindowTaskData(
          id = ID,
          shouldBeAvailable = available,
          contentFactory = factory,
          pluginDescriptor = if (bundled) null else DefaultPluginDescriptor(ID),
        ), buttonManager,
      ).toolWindow
      check(manager, window)
    }
    finally {
      Disposer.dispose(manager)
    }
  }
}
