// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests the contract of [ToolWindowEditorTabActionBase]: when the base class calls its subclass, and what it passes.
 *
 * The restored tab whose content is not created yet is covered in [ToolWindowEditorTabPendingContentTest], which owns
 * the fixture for such a tab.
 */
@TestApplication
class ToolWindowEditorTabActionBaseTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project: Project get() = projectFixture.get()

  private val toolWindowId = "TestToolWindow"
  private lateinit var toolWindow: ToolWindow

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(id = toolWindowId))
  }

  private fun createEvent(action: RecordingEditorTabAction, editor: FileEditor?, project: Project? = this.project): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.FILE_EDITOR, editor)
      .build()
    return TestActionEvent.createTestEvent(action, dataContext)
  }

  private fun tabEditor(toolWindowId: String = this.toolWindowId): FakeFileEditor =
    FakeFileEditor(createTabFile(project = project, toolWindowId = toolWindowId))

  /**
   * Runs both entry points of [action] on [event] and checks that the base class hides the action and calls no subclass method.
   */
  private fun assertHidden(action: RecordingEditorTabAction, event: AnActionEvent) {
    action.update(event)
    action.actionPerformed(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
    assertThat(action.updatedContents).isEmpty()
    assertThat(action.performedContents).isEmpty()
  }

  @Test
  fun `update passes the tool window and the content of the tab to the subclass`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val content = createTabContent(displayName = "tab")
      val editor = FakeFileEditor(createTabFile(project = project, toolWindowId = toolWindowId, content = content))
      val action = RecordingEditorTabAction()
      val event = createEvent(action, editor)

      action.update(event)

      assertThat(event.presentation.isEnabledAndVisible).isTrue()
      assertThat(action.updatedToolWindows).containsExactly(toolWindow)
      assertThat(action.updatedContents).containsExactly(content)
    }

  @Test
  fun `actionPerformed passes the content of the tab to the subclass`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val content = createTabContent(displayName = "tab")
      val editor = FakeFileEditor(createTabFile(project = project, toolWindowId = toolWindowId, content = content))
      val action = RecordingEditorTabAction()

      action.actionPerformed(createEvent(action, editor))

      assertThat(action.performedContents).containsExactly(content)
    }

  @Test
  fun `the action is hidden without a project`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val action = RecordingEditorTabAction()

    assertHidden(action, createEvent(action, tabEditor(), project = null))
  }

  @Test
  fun `the action is hidden without a file editor`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val action = RecordingEditorTabAction()

    assertHidden(action, createEvent(action, editor = null))
  }

  @Test
  fun `the action is hidden for the editor of a plain file`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val action = RecordingEditorTabAction()

    assertHidden(action, createEvent(action, FakeFileEditor(LightVirtualFile("plain.txt"))))
  }

  @Test
  fun `the action is hidden when the tool window of the tab is not registered`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val action = RecordingEditorTabAction()

      assertHidden(action, createEvent(action, tabEditor(toolWindowId = "UnregisteredToolWindow")))
    }

  @Test
  fun `the action is hidden for a tab without content`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    // The tab of a failed restore: the file exists, but it has neither content nor a stored state.
    // This action shows itself for content and for a stored state, so only the base class can hide it.
    val action = PendingContentEditorTabAction()
    val editor = FakeFileEditor(ToolWindowEditorTabFile(toolWindowId = toolWindowId, persistentPath = null))

    assertHidden(action, createEvent(action, editor))
  }

  @Test
  fun `the action updates on the EDT`() {
    assertThat(RecordingEditorTabAction().actionUpdateThread).isEqualTo(ActionUpdateThread.EDT)
  }

  /**
   * A file editor that only knows its file, which is all the base class reads from the editor.
   */
  private class FakeFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    override fun getFile(): VirtualFile = file
    override fun getComponent(): JComponent = JPanel()
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "fake"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
  }
}
