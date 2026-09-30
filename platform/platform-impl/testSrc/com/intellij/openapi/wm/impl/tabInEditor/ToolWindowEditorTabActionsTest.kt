// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests [MoveToolWindowTabToEditorAction], the "Open in Editor Tab" item of the tool window tab context menu.
 */
@TestApplication
class ToolWindowEditorTabActionsTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)
  private val registryFixture = registryKeyFixture(ToolWindowEditorTabSupportUtil.REGISTRY_KEY) { setValue(true) }

  private val project: Project get() = projectFixture.get()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  private val toolWindowId = "TestToolWindow"
  private val action = MoveToolWindowTabToEditorAction()

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    registryFixture.get()
    manager.closeAllFiles()
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
    registerFakeToolWindowEditorTabSupport(toolWindowId, FakeToolWindowEditorTabSupport(flowOf(ToolWindowEditorTabPresentation("Tab"))), disposable)
  }

  /**
   * A tool window backed by a real [ContentManager]. The headless mock does not carry the id and ignores `hide()`,
   * so the id is overridden and the hide calls are counted.
   */
  private class HideRecordingToolWindow(
    project: Project,
    private val id: String,
    private val contentManager: ContentManager,
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var hideCount: Int = 0

    override fun getId(): String = id
    override fun getContentManager(): ContentManager = contentManager
    override fun hide(runnable: Runnable?) {
      hideCount++
    }
  }

  private fun createToolWindow(id: String = toolWindowId): HideRecordingToolWindow {
    val contentManager = ContentFactory.getInstance().createContentManager(false, project)
    Disposer.register(disposable, contentManager)
    return HideRecordingToolWindow(project, id, contentManager)
  }

  private fun addContent(toolWindow: HideRecordingToolWindow, displayName: String = "tab"): Content {
    val content = createTabContent(displayName = displayName)
    toolWindow.contentManager.addContent(content)
    return content
  }

  /**
   * Creates the event of the tab context menu: the tool window and its content manager, whose selected content is the tab.
   */
  private fun createEvent(toolWindow: HideRecordingToolWindow): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
      .add(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER, toolWindow.contentManager)
      .build()
    return TestActionEvent.createTestEvent(action, dataContext)
  }

  private fun openTabFiles(): List<ToolWindowEditorTabFile> = manager.openFiles.filterIsInstance<ToolWindowEditorTabFile>()

  @Test
  fun `the action is available for the selected content of a supported tool window`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      addContent(toolWindow)
      val event = createEvent(toolWindow)

      action.update(event)

      assertThat(event.presentation.isEnabledAndVisible).isTrue()
    }

  @Test
  fun `the action is hidden while the feature is disabled`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      addContent(toolWindow)
      val event = createEvent(toolWindow)

      val registryValue = Registry.get(ToolWindowEditorTabSupportUtil.REGISTRY_KEY)
      registryValue.setValue(false)
      try {
        action.update(event)
      }
      finally {
        registryValue.setValue(true)
      }

      assertThat(event.presentation.isEnabledAndVisible).isFalse()
    }

  @Test
  fun `the action is hidden for a tool window without support`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow("UnsupportedToolWindow")
      addContent(toolWindow)
      val event = createEvent(toolWindow)

      action.update(event)

      assertThat(event.presentation.isEnabledAndVisible).isFalse()
    }

  @Test
  fun `the action is hidden without a selected content`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      val event = createEvent(toolWindow)

      action.update(event)

      assertThat(event.presentation.isEnabledAndVisible).isFalse()
    }

  @Test
  fun `moving the last content hides the tool window`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      val content = addContent(toolWindow)
      val event = createEvent(toolWindow)

      action.actionPerformed(event)

      assertThat(openTabFiles().single().attachedContent(project)).isSameAs(content)
      assertThat(toolWindow.contentManager.contents).isEmpty()
      assertThat(toolWindow.hideCount).isEqualTo(1)
    }

  @Test
  fun `moving one of several contents keeps the tool window visible`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val toolWindow = createToolWindow()
      val moved = addContent(toolWindow, displayName = "moved")
      val remaining = addContent(toolWindow, displayName = "remaining")
      toolWindow.contentManager.setSelectedContent(moved)
      val event = createEvent(toolWindow)

      action.actionPerformed(event)

      assertThat(openTabFiles().single().attachedContent(project)).isSameAs(moved)
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(remaining)
      assertThat(toolWindow.hideCount).isZero()
    }
}
