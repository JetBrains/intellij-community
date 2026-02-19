package com.intellij.agent.workbench.chat

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.FileEditorManagerTestCase
import org.assertj.core.api.Assertions.assertThat

class AgentChatEditorServiceTest : FileEditorManagerTestCase() {
  private val projectPath = "/work/project-a"
  private val codexCommand = listOf("codex", "resume", "thread-1")
  private val claudeCommand = listOf("claude", "--resume", "session-1")

  override fun setUp() {
    super.setUp()
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(
      TestChatFileEditorProvider(),
      LoadingOrder.FIRST,
      testRootDisposable,
    )
  }

  fun testReuseEditorForThread() {
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = null,
    )
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
  }

  fun testReuseEditorUpdatesTitleForThread() {
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Renamed thread",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
    assertThat(files.single().threadTitle).isEqualTo("Renamed thread")
    assertThat(EditorTabPresentationUtil.getEditorTabTitle(project, files.single())).isEqualTo("Renamed thread")
  }

  fun testSeparateTabsForSubAgents() {
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = "alpha",
    )
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = "beta",
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(2)
  }

  fun testTabTitleUsesThreadTitle() {
    val title = "Investigate crash"

    openChatOnEdt(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = title,
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    assertThat(file.threadTitle).isEqualTo(title)
    assertThat(EditorTabPresentationUtil.getEditorTabTitle(project, file)).isEqualTo(title)
  }

  fun testDifferentSessionIdentitiesDoNotReuseTab() {
    openChatOnEdt(
      threadIdentity = "CODEX:session-1",
      shellCommand = codexCommand,
      threadId = "session-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    openChatOnEdt(
      threadIdentity = "CLAUDE:session-1",
      shellCommand = claudeCommand,
      threadId = "session-1",
      threadTitle = "Thread",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(2)
  }

  fun testArchiveCleanupClosesOpenTabsAndDeletesMetadata() {
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Main thread",
      subAgentId = null,
    )
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Main thread",
      subAgentId = "alpha",
    )
    openChatOnEdt(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Other thread",
      subAgentId = null,
    )

    val store = AgentChatTabMetadataStores.getInstance()
    val beforeCleanup = openedChatFiles()
    assertThat(beforeCleanup).hasSize(3)
    val matchingTabKeys = beforeCleanup
      .filter { it.threadIdentity == "CODEX:thread-1" }
      .map { it.tabKey }
    val unrelatedTabKey = beforeCleanup.first { it.threadIdentity == "CODEX:thread-2" }.tabKey

    runWithModalProgressBlocking(project, "") {
      closeAndForgetAgentChatsForThread(
        projectPath = projectPath,
        threadIdentity = "CODEX:thread-1",
      )
    }

    val afterCleanup = openedChatFiles()
    assertThat(afterCleanup.map { it.threadIdentity })
      .containsExactly("CODEX:thread-2")
    for (tabKey in matchingTabKeys) {
      assertThat(store.loadDescriptor(tabKey)).isNull()
    }
    assertThat(store.loadDescriptor(unrelatedTabKey)).isNotNull
  }

  fun testValidationFailureDeletesMetadata() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:invalid-shell",
      threadId = "invalid-shell",
      threadTitle = "Invalid",
      subAgentId = null,
      shellCommand = emptyList(),
    )
    val store = AgentChatTabMetadataStores.getInstance()
    store.upsert(descriptor)
    try {
      val file = AgentChatVirtualFileSystems.getInstance().getOrCreateFile(descriptor)
      AgentChatFileEditorProvider().createEditor(project, file)

      assertThat(store.loadDescriptor(descriptor.tabKey)).isNull()
    }
    finally {
      store.delete(descriptor.tabKey)
    }
  }

  fun testTerminalInitializationFailureDeletesMetadata() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:init-failure",
      threadId = "init-failure",
      threadTitle = "Init failure",
      subAgentId = null,
      shellCommand = codexCommand,
    )
    val store = AgentChatTabMetadataStores.getInstance()
    store.upsert(descriptor)
    try {
      val file = AgentChatVirtualFileSystems.getInstance().getOrCreateFile(descriptor)
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, RuntimeException("boom"))

      assertThat(store.loadDescriptor(descriptor.tabKey)).isNull()
    }
    finally {
      store.delete(descriptor.tabKey)
    }
  }

  private fun openedChatFiles(): List<AgentChatVirtualFile> {
    return FileEditorManager.getInstance(project).openFiles.filterIsInstance<AgentChatVirtualFile>()
  }

  private fun openChatOnEdt(
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
  ) {
    runWithModalProgressBlocking(project, "") {
      openChat(
        project = project,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        shellCommand = shellCommand,
        threadId = threadId,
        threadTitle = threadTitle,
        subAgentId = subAgentId,
      )
    }
  }

  private class TestChatFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
      return file is AgentChatVirtualFile
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      return LightweightTestFileEditor(file, editorName = "AgentChatTestEditor")
    }

    override fun getEditorTypeId(): String = "agent.workbench-chat-editor-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}
