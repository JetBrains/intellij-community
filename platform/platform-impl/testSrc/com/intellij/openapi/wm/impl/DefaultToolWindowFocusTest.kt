// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.focusDefaultToolWindowOnStartup
import com.intellij.openapi.project.impl.presentEmptyEditorAfterStartupEditors
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val LEFT_ID = "LeftTestToolWindow"
private const val LEFT_SPLIT_ID = "LeftSplitTestToolWindow"
private const val BOTTOM_ID = "BottomTestToolWindow"
private const val RIGHT_ID = "RightTestToolWindow"
private const val HIDDEN_ID = "HiddenTestToolWindow"

/**
 * The focus rule of an editor area without an editor: [ToolWindowManagerImpl.focusDefaultToolWindow] and its startup step.
 * A headless test cannot create a frame, so the manager has no tool window pane, and no tool window is active.
 */
@TestApplication
internal class DefaultToolWindowFocusTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a claim of the empty state keeps the focus`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = true)).isNull()
      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(LEFT_ID)
    }
  }

  @Test
  fun `an active tool window keeps the focus`(): Unit = timeoutRunBlocking {
    val manager = createManager(activeId = BOTTOM_ID)
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)).isNull()
      manager.focusDefaultToolWindow(emptyStateClaimsFocus = false)
      assertThat(manager.getRecentToolWindows()).isEmpty()
    }
  }

  @Test
  fun `the active stack comes first`(): Unit = timeoutRunBlocking {
    val manager = createManager(recentToolWindows = listOf(LEFT_ID))
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM)
      registerToolWindow(manager, RIGHT_ID, ToolWindowAnchor.RIGHT)
      manager.activeStack.push(manager.getEntry(RIGHT_ID)!!)
      manager.activeStack.push(manager.getEntry(BOTTOM_ID)!!)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(BOTTOM_ID)
    }
  }

  @Test
  fun `the persistent active stack comes after the active stack`(): Unit = timeoutRunBlocking {
    val manager = createManager(recentToolWindows = listOf(LEFT_ID))
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM)
      manager.activeStack.push(manager.getEntry(BOTTOM_ID)!!)
      // the activation of an editor clears the active stack, but not the persistent one
      manager.activeStack.clear()

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(BOTTOM_ID)
    }
  }

  @Test
  fun `the first visible recent tool window comes after the active stacks`(): Unit = timeoutRunBlocking {
    val manager = createManager(recentToolWindows = listOf(HIDDEN_ID, "NotRegistered", RIGHT_ID, BOTTOM_ID))
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM)
      registerToolWindow(manager, RIGHT_ID, ToolWindowAnchor.RIGHT)
      registerToolWindow(manager, HIDDEN_ID, ToolWindowAnchor.BOTTOM, isSplit = true, isVisible = false)
      // an entry that is not visible is skipped in each stack
      manager.activeStack.push(manager.getEntry(HIDDEN_ID)!!)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(RIGHT_ID)
    }
  }

  @Test
  fun `the layout order comes last`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, RIGHT_ID, ToolWindowAnchor.RIGHT, order = 0)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM, order = 0)
      registerToolWindow(manager, LEFT_ID, ToolWindowAnchor.LEFT, order = 3)
      registerToolWindow(manager, LEFT_SPLIT_ID, ToolWindowAnchor.LEFT, isSplit = true, order = 1)
      registerToolWindow(manager, HIDDEN_ID, ToolWindowAnchor.LEFT, isSplit = true, order = 0, isVisible = false)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(LEFT_SPLIT_ID)
    }
  }

  @Test
  fun `the bottom anchor comes before the right anchor in the layout order`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, RIGHT_ID, ToolWindowAnchor.RIGHT, order = 0)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM, order = 5)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)?.id).isEqualTo(BOTTOM_ID)
    }
  }

  @Test
  fun `nothing is focused without a visible tool window`(): Unit = timeoutRunBlocking {
    val manager = createManager(recentToolWindows = listOf(HIDDEN_ID))
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, HIDDEN_ID, ToolWindowAnchor.LEFT, isVisible = false)

      assertThat(manager.findDefaultToolWindowToFocus(emptyStateClaimsFocus = false)).isNull()
    }
  }

  @Test
  fun `an open without editors focuses the visible Project view when nothing claims the focus`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, ToolWindowId.PROJECT_VIEW, ToolWindowAnchor.LEFT)
      registerToolWindow(manager, BOTTOM_ID, ToolWindowAnchor.BOTTOM)
    }

    focusDefaultToolWindowOnStartup(project, completedJob(), emptyStateClaimsFocus = { false })

    // the activation puts the tool window first in the recent tool windows
    assertThat(manager.getRecentToolWindows()).containsExactly(ToolWindowId.PROJECT_VIEW)
  }

  @Test
  fun `a claiming empty state keeps the focus after the settlement`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, ToolWindowId.PROJECT_VIEW, ToolWindowAnchor.LEFT)
    }
    val settled = CompletableDeferred<Unit>()
    val emptyStateClaimKept = AtomicBoolean(true)

    // the same order as the project open: the empty state settles its claim, then the focus step runs
    presentEmptyEditorAfterStartupEditors(
      finishOpeningStartupEditors = {},
      presentEmptyEditor = {
        // the empty state took the focus, so its claim is kept
        settled.complete(Unit)
        settled.await()
      },
    )
    focusDefaultToolWindowOnStartup(project, completedJob(), emptyStateClaimsFocus = { emptyStateClaimKept.get() })

    assertThat(manager.getRecentToolWindows()).isEmpty()
  }

  @Test
  fun `the startup step reads the claim when it runs`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    withContext(Dispatchers.EDT) {
      registerToolWindow(manager, ToolWindowId.PROJECT_VIEW, ToolWindowAnchor.LEFT)
    }
    val toolWindowInitJob = Job()
    val emptyStateClaimKept = AtomicBoolean(true)

    val step = async {
      focusDefaultToolWindowOnStartup(project, toolWindowInitJob, emptyStateClaimsFocus = { emptyStateClaimKept.get() })
    }
    // the empty state gives up its claim before the step runs, for example, because it built nothing
    emptyStateClaimKept.set(false)
    toolWindowInitJob.complete()
    step.await()

    assertThat(manager.getRecentToolWindows()).containsExactly(ToolWindowId.PROJECT_VIEW)
  }

  @Test
  fun `the startup step does not wait for the readiness of the Project view`(): Unit = timeoutRunBlocking {
    val manager = createManager()
    project.replaceService(ToolWindowManager::class.java, manager, disposable)
    val projectView = withContext(Dispatchers.EDT) {
      registerToolWindow(manager, ToolWindowId.PROJECT_VIEW, ToolWindowAnchor.LEFT)
    }

    // the Project view of a headless test never becomes ready, because its component never shows
    withTimeout(2.seconds) {
      focusDefaultToolWindowOnStartup(project, completedJob(), emptyStateClaimsFocus = { false })
    }

    assertThat(projectView.getReady(this).isDone).isFalse()
    assertThat(manager.getRecentToolWindows()).containsExactly(ToolWindowId.PROJECT_VIEW)
  }

  private fun completedJob(): Job = Job().also { it.complete() }

  private fun createManager(
    activeId: String? = null,
    recentToolWindows: List<String> = emptyList(),
  ): ToolWindowManagerImpl {
    val state = ToolWindowManagerStateImpl()
    state.noStateLoaded()
    state.recentToolWindows.addAll(recentToolWindows)
    project.replaceService(ToolWindowManagerState::class.java, state, disposable)

    val buttonManager = ToolWindowPaneOldButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    val coroutineScope = (project as ComponentManagerEx).getCoroutineScope()
    val manager = object : ToolWindowManagerImpl(project, isNewUi = false, isEdtRequired = false, coroutineScope = coroutineScope) {
      override val activeToolWindowId: String?
        get() = activeId

      override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager

      override fun getButtonManager(paneId: String): ToolWindowButtonManager = buttonManager
    }
    Disposer.register(disposable, manager)
    Disposer.register(disposable) {
      val actionManager = ActionManager.getInstance()
      for (id in manager.toolWindowIdSet) {
        val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(id)
        if (actionManager.getAction(actionId) != null) {
          actionManager.unregisterAction(actionId)
        }
      }
    }
    return manager
  }

  /** Registers a tool window that the layout places at [anchor], and shows it if [isVisible] is true. */
  private fun registerToolWindow(
    manager: ToolWindowManagerImpl,
    id: String,
    anchor: ToolWindowAnchor,
    isSplit: Boolean = false,
    order: Int = -1,
    isVisible: Boolean = true,
  ): ToolWindow {
    manager.layoutState.addInfo(id, WindowInfoImpl().also {
      it.id = id
      it.anchor = anchor
      it.isSplit = isSplit
      it.order = order
      it.isVisible = isVisible
    })
    val task = RegisterToolWindowTaskData(id = id, anchor = anchor, contentFactory = EmptyToolWindowFactory())
    val toolWindow = manager.registerToolWindow(task, manager.getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)).toolWindow
    assertThat(toolWindow.isVisible).isEqualTo(isVisible)
    return toolWindow
  }
}

private class EmptyToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
