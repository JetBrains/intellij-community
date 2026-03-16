package com.intellij.agent.workbench.chat

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class AgentChatEditorServiceTest : FileEditorManagerTestCase() {
  private val codexCommand = listOf("codex", "resume", "thread-1")
  private val claudeCommand = listOf("claude", "--resume", "session-1")

  override fun setUp() {
    super.setUp()
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(TestChatFileEditorProvider(), testRootDisposable)
  }

  fun testReuseEditorForThread() {
    val service = project.service<AgentChatEditorService>()

    runInEdtAndWait {
      service.openChat("/work/project-a", "CODEX:thread-1", codexCommand, "thread-1", "Fix auth bug", null)
      service.openChat("/work/project-a", "CODEX:thread-1", codexCommand, "thread-1", "Fix auth bug", null)
    }

    val files = openedChatFiles()
    assertEquals(1, files.size)
  }

  fun testSeparateTabsForSubAgents() {
    val service = project.service<AgentChatEditorService>()

    runInEdtAndWait {
      service.openChat("/work/project-a", "CODEX:thread-1", codexCommand, "thread-1", "Fix auth bug", "alpha")
      service.openChat("/work/project-a", "CODEX:thread-1", codexCommand, "thread-1", "Fix auth bug", "beta")
    }

    val files = openedChatFiles()
    assertEquals(2, files.size)
  }

  fun testTabTitleUsesThreadTitle() {
    val service = project.service<AgentChatEditorService>()
    val title = "Investigate crash"

    runInEdtAndWait {
      service.openChat(
        "/work/project-a",
        "CODEX:thread-2",
        listOf("codex", "resume", "thread-2"),
        "thread-2",
        title,
        null,
      )
    }

    val file = openedChatFiles().single()
    assertEquals(title, file.name)
  }

  fun testDifferentSessionIdentitiesDoNotReuseTab() {
    val service = project.service<AgentChatEditorService>()

    runInEdtAndWait {
      service.openChat("/work/project-a", "CODEX:session-1", codexCommand, "session-1", "Thread", null)
      service.openChat("/work/project-a", "CLAUDE:session-1", claudeCommand, "session-1", "Thread", null)
    }

    val files = openedChatFiles()
    assertEquals(2, files.size)
  }

  private fun openedChatFiles(): List<AgentChatVirtualFile> {
    return FileEditorManager.getInstance(project).openFiles.filterIsInstance<AgentChatVirtualFile>()
  }

  private class TestChatFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return file is AgentChatVirtualFile
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      return TestChatFileEditor(file)
    }

    override fun getEditorTypeId(): String = "agent.workbench-chat-editor-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  private class TestChatFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val component = JPanel()

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = "AgentChatTestEditor"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() = Unit
  }
}
