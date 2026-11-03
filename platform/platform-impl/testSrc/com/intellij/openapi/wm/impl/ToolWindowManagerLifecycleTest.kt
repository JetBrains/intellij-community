// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToggleDockModeAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val ID = "LifecycleTestToolWindow"

/**
 * The manager that an action of a tool-window title acts on, see [getToolWindowManagerForEvent].
 * A headless test cannot create a frame, so the manager has no tool window pane, and the test manager names the
 * active tool window itself.
 */
@TestApplication
internal class ToolWindowManagerLifecycleTest {
  private val project by projectFixture()
  private val otherProject by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a title action resolves the manager of the tool window in its context`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    withContext(Dispatchers.EDT) {
      val toolWindow = registerToolWindow(manager)
      // the title data context of a frame with guests gives the active project, which can differ from the project of the manager
      val context = DataContext { dataId ->
        when {
          CommonDataKeys.PROJECT.`is`(dataId) -> otherProject
          PlatformDataKeys.TOOL_WINDOW.`is`(dataId) -> toolWindow
          else -> null
        }
      }
      val action = object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
        }
      }
      val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.TOOLWINDOW_TITLE, ActionUiKind.NONE, null)

      assertThat(getToolWindowManagerForEvent(event)).isSameAs(manager)
    }
  }

  @Test
  fun `a view-mode toggle acts on the tool window of its context`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    withContext(Dispatchers.EDT) {
      val toolWindow = registerToolWindow(manager)
      // the project of the context is a frame guest: its own manager shows no tool window
      val context = DataContext { dataId ->
        when {
          CommonDataKeys.PROJECT.`is`(dataId) -> otherProject
          PlatformDataKeys.TOOL_WINDOW.`is`(dataId) -> toolWindow
          else -> null
        }
      }
      @Suppress("DEPRECATION")
      val action = ToggleDockModeAction()
      val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.TOOLWINDOW_TITLE, ActionUiKind.NONE, null)

      action.update(event)
      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(action.isSelected(event)).isTrue()
    }
  }

  @Test
  fun `an event without a tool window resolves the manager of the frame owner of its project`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    // the record of the test window manager: `otherProject` is a frame guest of `project`
    val windowManager = WindowManagerEx.getInstanceEx()
    windowManager.addFrameGuest(project, otherProject)
    try {
      val context = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) otherProject else null }
      val event = AnActionEvent.createEvent(context, null, ActionPlaces.MAIN_MENU, ActionUiKind.NONE, null)

      assertThat(getToolWindowManagerForEvent(event)).isSameAs(manager)
    }
    finally {
      windowManager.removeFrameGuest(otherProject)
    }
  }

  private fun createManager(): ToolWindowManagerImpl {
    val state = ToolWindowManagerStateImpl()
    state.noStateLoaded()
    project.replaceService(ToolWindowManagerState::class.java, state, disposable)

    val buttonManager = ToolWindowPaneOldButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    val manager = object : ToolWindowManagerImpl(project, isNewUi = false, isEdtRequired = false, coroutineScope = coroutineScope) {
      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager

      override fun getButtonManager(paneId: String): ToolWindowButtonManager = buttonManager

      // the platform manager reads the focus of the frame, and the test has no frame
      override val activeToolWindowId: String?
        get() = if (idToEntry.containsKey(ID)) ID else null
    }
    Disposer.register(disposable, manager)
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(ID)
      if (actionManager.getAction(actionId) != null) {
        actionManager.unregisterAction(actionId)
      }
    }
    return manager
  }

  private fun registerToolWindow(manager: ToolWindowManagerImpl): ToolWindow {
    manager.layoutState.addInfo(ID, WindowInfoImpl().also {
      it.id = ID
      it.anchor = ToolWindowAnchor.LEFT
      it.isVisible = true
    })
    val contentFactory = object : ToolWindowFactory {
      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      }
    }
    val task = RegisterToolWindowTaskData(id = ID, anchor = ToolWindowAnchor.LEFT, contentFactory = contentFactory)
    return manager.registerToolWindow(task, manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)).toolWindow
  }
}
