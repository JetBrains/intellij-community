package com.intellij.agent.workbench.chat

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
import kotlinx.coroutines.runBlocking
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
    runInEdtAndWait {
      runBlocking {
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Fix auth bug",
          subAgentId = null,
        )
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Fix auth bug",
          subAgentId = null,
        )
      }
    }

    val files = openedChatFiles()
    assertEquals(1, files.size)
  }

  fun testReuseEditorUpdatesTitleForThread() {
    runInEdtAndWait {
      runBlocking {
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Thread",
          subAgentId = null,
        )
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Renamed thread",
          subAgentId = null,
        )
      }
    }

    val files = openedChatFiles()
    assertEquals(1, files.size)
    assertEquals("Renamed thread", files.single().name)
  }

  fun testSeparateTabsForSubAgents() {
    runInEdtAndWait {
      runBlocking {
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Fix auth bug",
          subAgentId = "alpha",
        )
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-1",
          shellCommand = codexCommand,
          threadId = "thread-1",
          threadTitle = "Fix auth bug",
          subAgentId = "beta",
        )
      }
    }

    val files = openedChatFiles()
    assertEquals(2, files.size)
  }

  fun testTabTitleUsesThreadTitle() {
    val title = "Investigate crash"

    runInEdtAndWait {
      runBlocking {
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-2",
          shellCommand = listOf("codex", "resume", "thread-2"),
          threadId = "thread-2",
          threadTitle = title,
          subAgentId = null,
        )
      }
    }

    val file = openedChatFiles().single()
    assertEquals(title, file.name)
  }

  fun testDifferentSessionIdentitiesDoNotReuseTab() {
    runInEdtAndWait {
      runBlocking {
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:session-1",
          shellCommand = codexCommand,
          threadId = "session-1",
          threadTitle = "Thread",
          subAgentId = null,
        )
        openChat(
          project = project,
          projectPath = "/work/project-a",
          threadIdentity = "CLAUDE:session-1",
          shellCommand = claudeCommand,
          threadId = "session-1",
          threadTitle = "Thread",
          subAgentId = null,
        )
      }
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
