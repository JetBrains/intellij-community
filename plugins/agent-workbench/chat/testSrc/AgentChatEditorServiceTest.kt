package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AgentChatEditorServiceTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project get() = projectFixture.get()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  private val projectPath = "/work/project-a"
  private val codexCommand = listOf("codex", "resume", "thread-1")
  private val claudeCommand = listOf("claude", "--resume", "session-1")

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    runInUi {
      fileEditorManagerFixture.get()
      FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(
        TestChatFileEditorProvider(),
        LoadingOrder.FIRST,
        project,
      )
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    runInUi {
      (VirtualFileManager.getInstance().getFileSystem(AGENT_CHAT_PROTOCOL) as? AgentChatVirtualFileSystem)?.clearFilesForTests()
    }
  }

  @Test
  fun testReuseEditorForThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
  }

  @Test
  fun testReuseEditorUpdatesTitleForThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Renamed thread",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
    assertThat(files.single().threadTitle).isEqualTo("Renamed thread")
    assertThat(editorTabTitle(files.single())).isEqualTo("Renamed thread")
  }

  @Test
  fun testSeparateTabsForSubAgents(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = "alpha",
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Fix auth bug",
      subAgentId = "beta",
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(2)
  }

  @Test
  fun testTabTitleUsesThreadTitle(): Unit = timeoutRunBlocking {
    val title = "Investigate crash"

    openChatInModal(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = title,
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    assertThat(file.threadTitle).isEqualTo(title)
    assertThat(editorTabTitle(file)).isEqualTo(title)
    assertThat(editorTabTooltip(file)).isEqualTo(title)
  }

  @Test
  fun testLongTabTitleUsesMiddleTruncationAndTooltipKeepsFullTitle(): Unit = timeoutRunBlocking {
    val longTitle = "Project setup: " + "a".repeat(180) + " tail"

    openChatInModal(
      threadIdentity = "CODEX:thread-long-title",
      shellCommand = listOf("codex", "resume", "thread-long-title"),
      threadId = "thread-long-title",
      threadTitle = longTitle,
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    assertThat(file.threadTitle).isEqualTo(longTitle)
    assertThat(editorTabTitle(file)).isEqualTo(StringUtil.trimMiddle(longTitle, 50))
    assertThat(editorTabTitle(file)).isNotEqualTo(longTitle)
    assertThat(editorTabTooltip(file)).isEqualTo(longTitle)
  }

  @Test
  fun testUpdateOpenChatTabPresentationRefreshesExistingTabTitleAndActivity(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Initial title",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val threadKey = file.projectPath to file.threadIdentity
    val updatedTabs = runInUi {
      updateOpenAgentChatTabPresentation(
        titleByPathAndThreadIdentity = mapOf(
          threadKey to "Renamed by source update",
        ),
        activityByPathAndThreadIdentity = mapOf(
          threadKey to AgentThreadActivity.UNREAD,
        ),
      )
    }
    assertThat(updatedTabs).isEqualTo(1)

    assertThat(file.threadTitle).isEqualTo("Renamed by source update")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(editorTabTitle(file)).isEqualTo("Renamed by source update")

    val unchangedTabs = runInUi {
      updateOpenAgentChatTabPresentation(
        titleByPathAndThreadIdentity = mapOf(
          threadKey to "Renamed by source update",
        ),
        activityByPathAndThreadIdentity = mapOf(
          threadKey to AgentThreadActivity.UNREAD,
        ),
      )
    }
    assertThat(unchangedTabs).isEqualTo(0)
  }

  @Test
  fun testRebindOpenPendingChatTabToConcreteThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val reboundTabs = rebindOpenAgentChatPendingTabs(
      targetsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindTarget(
            threadIdentity = "CODEX:thread-3",
            threadId = "thread-3",
            shellCommand = listOf("codex", "resume", "thread-3"),
            threadTitle = "Recovered thread",
            threadActivity = AgentThreadActivity.UNREAD,
          )
        )
      )
    )
    assertThat(reboundTabs).isEqualTo(1)

    assertThat(file.threadIdentity).isEqualTo("CODEX:thread-3")
    assertThat(file.threadId).isEqualTo("thread-3")
    assertThat(file.shellCommand).containsExactly("codex", "resume", "thread-3")
    assertThat(file.threadTitle).isEqualTo("Recovered thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)

    openChatInModal(
      threadIdentity = "CODEX:thread-3",
      shellCommand = listOf("codex", "resume", "thread-3"),
      threadId = "thread-3",
      threadTitle = "Recovered thread",
      subAgentId = null,
    )
    assertThat(openedChatFiles()).hasSize(1)
  }

  @Test
  fun testCollectOpenPendingProjectPathsIncludesOnlyCodexPendingTabs(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = claudeCommand,
      threadId = "",
      threadTitle = "Claude pending",
      subAgentId = null,
    )

    assertThat(collectOpenPendingAgentChatProjectPaths()).isEmpty()

    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = codexCommand,
      threadId = "",
      threadTitle = "Codex pending",
      subAgentId = null,
    )

    assertThat(collectOpenPendingAgentChatProjectPaths()).containsExactly(projectPath)
  }

  @Test
  fun testDifferentSessionIdentitiesDoNotReuseTab(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:session-1",
      shellCommand = codexCommand,
      threadId = "session-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CLAUDE:session-1",
      shellCommand = claudeCommand,
      threadId = "session-1",
      threadTitle = "Thread",
      subAgentId = null,
    )

    val files = openedChatFiles()
    assertThat(files).hasSize(2)
  }

  @Test
  fun testArchiveCleanupClosesOpenTabsAndDeletesMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Main thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Main thread",
      subAgentId = "alpha",
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Other thread",
      subAgentId = null,
    )

    val tabsService = service<AgentChatTabsService>()
    val beforeCleanup = openedChatFiles()
    assertThat(beforeCleanup).hasSize(3)
    val matchingTabKeys = beforeCleanup
      .filter { it.threadIdentity == "CODEX:thread-1" }
      .map { it.tabKey }
    val unrelatedTabKey = beforeCleanup.first { it.threadIdentity == "CODEX:thread-2" }.tabKey

    closeAndForgetAgentChatsForThread(
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-1",
    )

    val afterCleanup = openedChatFiles()
    assertThat(afterCleanup.map { it.threadIdentity })
      .containsExactly("CODEX:thread-2")
    for (tabKey in matchingTabKeys) {
      assertThat(tabsService.load(tabKey)).isNull()
    }
    assertThat(tabsService.load(unrelatedTabKey)).isNotNull
  }

  @Test
  fun testValidationFailureDeletesMetadata(): Unit = timeoutRunBlocking {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:invalid-shell",
      threadId = "invalid-shell",
      threadTitle = "Invalid",
      subAgentId = null,
      shellCommand = emptyList(),
    )
    val tabsService = service<AgentChatTabsService>()
    tabsService.upsert(snapshot)
    try {
      val file = agentChatVirtualFileSystem().getOrCreateFile(snapshot)
      runInUi {
        AgentChatFileEditorProvider().createEditor(project, file)
      }

      assertThat(tabsService.load(snapshot.tabKey.value)).isNull()
    }
    finally {
      tabsService.forget(snapshot.tabKey)
    }
  }

  @Test
  fun testTerminalInitializationFailureDeletesMetadata(): Unit = timeoutRunBlocking {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:init-failure",
      threadId = "init-failure",
      threadTitle = "Init failure",
      subAgentId = null,
      shellCommand = codexCommand,
    )
    val tabsService = service<AgentChatTabsService>()
    tabsService.upsert(snapshot)
    try {
      val file = agentChatVirtualFileSystem().getOrCreateFile(snapshot)
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, RuntimeException("boom"))

      assertThat(tabsService.load(snapshot.tabKey.value)).isNull()
    }
    finally {
      tabsService.forget(snapshot.tabKey)
    }
  }

  private suspend fun openedChatFiles(): List<AgentChatVirtualFile> {
    return runInUi {
      FileEditorManager.getInstance(project).openFiles.filterIsInstance<AgentChatVirtualFile>()
    }
  }

  private suspend fun openChatInModal(
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
  ) {
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
    )
    waitForCondition {
      openedChatFiles().any { file ->
        file.threadIdentity == threadIdentity &&
        file.subAgentId == subAgentId &&
        file.threadId == threadId &&
        file.threadTitle == threadTitle &&
        file.shellCommand == shellCommand
      }
    }
  }

  private suspend fun editorTabTitle(file: AgentChatVirtualFile): String {
    return runInUi {
      EditorTabPresentationUtil.getEditorTabTitle(project, file)
    }
  }

  private suspend fun editorTabTooltip(file: AgentChatVirtualFile): String? {
    return runInUi {
      AgentChatEditorTabTitleProvider().getEditorTabTooltipText(project, file)
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

private suspend fun <T> runInUi(action: suspend () -> T): T {
  return withContext(Dispatchers.UiWithModelAccess) {
    action()
  }
}

private suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
