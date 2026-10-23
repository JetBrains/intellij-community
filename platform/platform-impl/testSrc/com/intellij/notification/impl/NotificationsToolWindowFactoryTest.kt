// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.productMode.ProductMode
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.util.application
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class NotificationsToolWindowFactoryTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the tool window is unavailable in a light mode only`() {
    val factory = NotificationsToolWindowFactory()
    for (mode in ProductMode.entries) {
      withProductMode(mode) {
        assertThat(factory.shouldBeAvailable(project)).describedAs(mode.id).isEqualTo(!mode.isLight)
      }
    }
  }

  @Test
  fun `a move between the light mode and the smart mode changes the availability`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val manager = createToolWindowManager()
    val toolWindow = manager.getToolWindow(NotificationsToolWindowFactory.ID)!!
    assertThat(toolWindow.isAvailable).isFalse()

    // the test runs on the EDT, so the factory subscribes before its first suspension
    val managing = launch(start = CoroutineStart.UNDISPATCHED) {
      NotificationsToolWindowFactory().manage(toolWindow, manager)
    }
    try {
      withProductMode(ProductMode.MONOLITH) {
        application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginsLoaded()
      }
      assertThat(toolWindow.isAvailable).isTrue()

      withProductMode(ProductMode.LIGHT_MONOLITH) {
        application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginsUnloaded()
      }
      assertThat(toolWindow.isAvailable).isFalse()
    }
    finally {
      managing.cancel()
    }
  }

  /** Registers the Notifications tool window the way a light process does: with no availability and with no content. */
  private fun createToolWindowManager(): ToolWindowManagerImpl {
    val buttonManager = ToolWindowPaneNewButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val manager = object : ToolWindowManagerImpl(project, isNewUi = true, isEdtRequired = false, (project as ComponentManagerEx).getCoroutineScope()) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)

    val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = true)
    layoutManager.noStateLoaded()
    manager.setLayoutOnInit(layoutManager.getLayoutCopy())

    val task = RegisterToolWindowTaskData(
      id = NotificationsToolWindowFactory.ID,
      anchor = ToolWindowAnchor.RIGHT,
      shouldBeAvailable = false,
      contentFactory = object : ToolWindowFactory {
        override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        }
      },
    )
    manager.registerToolWindow(task, buttonManager)
    return manager
  }

  /** Runs [block] with [IdeProductMode] reporting [mode]. The mode of the test application depends on the runner. */
  private fun withProductMode(mode: ProductMode, block: () -> Unit) {
    val previous = IdeProductMode.getInstance()
    application.replaceService(IdeProductMode::class.java, object : IdeProductMode {
      override val currentMode: ProductMode
        get() = mode
    }, disposable)
    try {
      block()
    }
    finally {
      application.replaceService(IdeProductMode::class.java, previous, disposable)
    }
  }
}
