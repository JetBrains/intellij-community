package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class AgentChatEditorServiceTest {
  companion object {
    private val CUSTOM_AGENT_CHAT_EDITOR_KEY: Key<Boolean> = Key.create("agent.workbench.chat.test.customEditor")

    @Volatile
    private var customFileEditorFactory: ((Project, AgentChatVirtualFile) -> FileEditor)? = null
  }

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
    withTimeout(30.seconds) {
      project.waitForSmartMode()
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
  fun testNewTabPersistsMultiStepInitialMessageMetadata(): Unit = timeoutRunBlocking {
    val steps = codexPlanDispatchSteps("Refactor selected code")
    openChatInModal(
      threadIdentity = "CODEX:thread-multi-step-new",
      shellCommand = codexCommand,
      threadId = "thread-multi-step-new",
      threadTitle = "Multi-step new thread",
      subAgentId = null,
      postStartDispatchSteps = steps,
      initialMessageToken = "multi-step-new-token",
    )

    val file = openedChatFiles().single()
    assertThat(file.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(file.initialComposedMessage).isEqualTo("/plan")
    assertThat(file.initialMessageToken).isEqualTo("multi-step-new-token")
    assertThat(file.initialMessageSent).isFalse()

    val persisted = checkNotNull(service<AgentChatTabsService>().load(file.tabKey))
    assertThat(persisted.runtime.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(persisted.runtime.initialMessageDispatchStepIndex).isZero()
    assertThat(persisted.runtime.initialMessageToken).isEqualTo("multi-step-new-token")
    assertThat(persisted.runtime.initialMessageSent).isFalse()
  }

  @Test
  fun testNewTabStartupCommandOverrideDoesNotPersistInitialMessageMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-startup-1",
      shellCommand = codexCommand,
      startupShellCommandOverride = listOf("codex", "--", "-draft prompt\nsecond line"),
      startupShellEnvOverride = mapOf("DISABLE_AUTOUPDATER" to "1"),
      threadId = "thread-startup-1",
      threadTitle = "Startup prompt thread",
      subAgentId = null,
      initialComposedMessage = "-draft prompt\nsecond line",
      initialMessageToken = "startup-token-1",
    )

    val file = openedChatFiles().single()
    assertThat(file.shellCommand).containsExactlyElementsOf(codexCommand)
    assertThat(file.initialComposedMessage).isNull()
    assertThat(file.initialMessageToken).isNull()
    assertThat(file.initialMessageSent).isFalse()
  }

  @Test
  fun testNewTabStartupLaunchSpecOverrideMergesEnvAndFallsBackToBase(): Unit = timeoutRunBlocking {
    val baseEnv = mapOf("PATH" to "/usr/local/bin", "TERM" to "xterm-256color")
    val startupEnv = mapOf("PATH" to "/custom/bin", "DISABLE_AUTOUPDATER" to "1")
    openChatInModal(
      threadIdentity = "CODEX:thread-startup-env",
      shellCommand = codexCommand,
      shellEnvVariables = baseEnv,
      startupShellCommandOverride = listOf("codex", "--", "-draft with env"),
      startupShellEnvOverride = startupEnv,
      threadId = "thread-startup-env",
      threadTitle = "Startup env thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    assertThat(file.shellCommand).containsExactlyElementsOf(codexCommand)
    assertThat(file.shellEnvVariables).containsExactlyEntriesOf(baseEnv)

    val startupLaunchSpec = file.consumeStartupLaunchSpec()
    assertThat(startupLaunchSpec.command).containsExactly("codex", "--", "-draft with env")
    assertThat(startupLaunchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/custom/bin", "TERM" to "xterm-256color", "DISABLE_AUTOUPDATER" to "1"))

    val fallbackLaunchSpec = file.consumeStartupLaunchSpec()
    assertThat(fallbackLaunchSpec.command).containsExactlyElementsOf(codexCommand)
    assertThat(fallbackLaunchSpec.envVariables).containsExactlyEntriesOf(baseEnv)
  }

  @Test
  fun testExistingTabIgnoresStartupCommandOverrideAndKeepsInitialMessageMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-existing-startup",
      shellCommand = codexCommand,
      threadId = "thread-existing-startup",
      threadTitle = "Existing thread",
      subAgentId = null,
    )

    openChatInModal(
      threadIdentity = "CODEX:thread-existing-startup",
      shellCommand = codexCommand,
      startupShellCommandOverride = listOf("codex", "--", "Should be ignored for open tab"),
      startupShellEnvOverride = mapOf("DISABLE_AUTOUPDATER" to "1"),
      threadId = "thread-existing-startup",
      threadTitle = "Existing thread",
      subAgentId = null,
      initialComposedMessage = "Send through open-tab flow",
      initialMessageToken = "startup-token-2",
    )

    val file = openedChatFiles().single()
    assertThat(file.shellCommand).containsExactlyElementsOf(codexCommand)
    assertThat(file.initialComposedMessage).isEqualTo("Send through open-tab flow")
    assertThat(file.initialMessageToken).isEqualTo("startup-token-2")
    assertThat(file.initialMessageSent).isFalse()
  }

  @Test
  fun testExistingTabReusePersistsMultiStepInitialMessageMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-existing-multi-step",
      shellCommand = codexCommand,
      threadId = "thread-existing-multi-step",
      threadTitle = "Existing multi-step thread",
      subAgentId = null,
    )

    val steps = codexPlanDispatchSteps("Follow-up prompt")
    openChatInModal(
      threadIdentity = "CODEX:thread-existing-multi-step",
      shellCommand = codexCommand,
      threadId = "thread-existing-multi-step",
      threadTitle = "Existing multi-step thread",
      subAgentId = null,
      postStartDispatchSteps = steps,
      initialMessageToken = "existing-multi-step-token",
    )

    val file = openedChatFiles().single()
    assertThat(file.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(file.initialMessageDispatchStepIndex).isZero()
    assertThat(file.initialComposedMessage).isEqualTo("/plan")
    assertThat(file.initialMessageToken).isEqualTo("existing-multi-step-token")
    assertThat(file.initialMessageSent).isFalse()

    val persisted = checkNotNull(service<AgentChatTabsService>().load(file.tabKey))
    assertThat(persisted.runtime.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(persisted.runtime.initialMessageDispatchStepIndex).isZero()
    assertThat(persisted.runtime.initialMessageToken).isEqualTo("existing-multi-step-token")
    assertThat(persisted.runtime.initialMessageSent).isFalse()
  }

  @Test
  fun testExistingTabReuseFlushesInitialMessageToOpenEditor(): Unit = timeoutRunBlocking {
    val terminalTabs = EditorServiceFakeAgentChatTerminalTabs()
    customFileEditorFactory = { editorProject, file ->
      AgentChatFileEditor(
        project = editorProject,
        file = file,
        terminalTabs = terminalTabs,
        tabSnapshotWriter = AgentChatTabSnapshotWriter { snapshot ->
          editorProject.service<AgentChatTabsService>().upsert(snapshot)
        },
      ).also { editor ->
        editor.putUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY, true)
      }
    }

    openChatInModal(
      threadIdentity = "CODEX:thread-existing-send",
      shellCommand = codexCommand,
      threadId = "thread-existing-send",
      threadTitle = "Existing send thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val editor = runInUi {
      FileEditorManager.getInstance(project).getAllEditors(file)
        .filterIsInstance<AgentChatFileEditor>()
        .single { candidate -> candidate.getUserData(CUSTOM_AGENT_CHAT_EDITOR_KEY) == true }
    }
    runInUi {
      editor.selectNotify()
    }
    assertThat(terminalTabs.createCalls).isEqualTo(1)
    terminalTabs.tab.setSessionState(TerminalViewSessionState.Running)

    openChatInModal(
      threadIdentity = "CODEX:thread-existing-send",
      shellCommand = codexCommand,
      threadId = "thread-existing-send",
      threadTitle = "Existing send thread",
      subAgentId = null,
      initialComposedMessage = "Send through already open editor",
      initialMessageToken = "existing-send-token",
    )

    waitForCondition { terminalTabs.tab.sentTexts.size == 1 }

    assertThat(file.initialMessageSent).isTrue()
    assertThat(terminalTabs.tab.sentTexts)
      .containsExactly(EditorServiceSentTerminalText("Send through already open editor", shouldExecute = true))

    val persisted = checkNotNull(service<AgentChatTabsService>().load(file.tabKey))
    assertThat(persisted.runtime.initialMessageSent).isTrue()
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
  }

  fun testUpdateOpenChatTabPresentationRefreshesExistingTabTitleAndActivity() {
    openChatOnEdt(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Initial title",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    runWithModalProgressBlocking(project, "") {
      val updatedTabs = updateOpenAgentChatTabPresentation(
        titleByPathAndThreadIdentity = mapOf(
          threadKey to "Renamed by source update",
        ),
        activityByPathAndThreadIdentity = mapOf(
          threadKey to AgentThreadActivity.UNREAD,
        ),
        activityByPathAndThreadIdentity = mapOf(
          (projectPath to "CODEX:thread-1") to AgentThreadActivity.UNREAD,
        ),
      )
    }
    assertThat(updatedTabs).isEqualTo(1)

    assertThat(file.threadTitle).isEqualTo("Renamed by source update")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(EditorTabPresentationUtil.getEditorTabTitle(project, file)).isEqualTo("Renamed by source update")

    runWithModalProgressBlocking(project, "") {
      val updatedTabs = updateOpenAgentChatTabPresentation(
        titleByPathAndThreadIdentity = mapOf(
          threadKey to "Renamed by source update",
        ),
        activityByPathAndThreadIdentity = mapOf(
          threadKey to AgentThreadActivity.UNREAD,
        ),
        activityByPathAndThreadIdentity = mapOf(
          (projectPath to "CODEX:thread-1") to AgentThreadActivity.UNREAD,
        ),
      )
    }
    assertThat(unchangedTabs).isEqualTo(0)
  }

  fun testRebindOpenPendingChatTabToConcreteThread() {
    openChatOnEdt(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    runWithModalProgressBlocking(project, "") {
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
    }

    assertThat(file.threadIdentity).isEqualTo("CODEX:thread-3")
    assertThat(file.threadId).isEqualTo("thread-3")
    assertThat(file.shellCommand).containsExactly("codex", "resume", "thread-3")
    assertThat(file.threadTitle).isEqualTo("Recovered thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)

    openChatOnEdt(
      threadIdentity = "CODEX:thread-3",
      shellCommand = listOf("codex", "resume", "thread-3"),
      threadId = "thread-3",
      threadTitle = "Recovered thread",
      subAgentId = null,
    )
    assertThat(openedChatFiles()).hasSize(1)
  }

  fun testDifferentSessionIdentitiesDoNotReuseTab() {
    openChatOnEdt(
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
  fun testResolveFromPathSkipsRestoreWhenVersionMismatch(): Unit = timeoutRunBlocking {
    val tabsService = service<AgentChatTabsService>()
    val stateService = service<AgentChatTabsStateService>()
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:version-mismatch-restore",
      threadId = "version-mismatch-restore",
      threadTitle = "Version mismatch",
      subAgentId = null,
      shellCommand = codexCommand,
    )
    tabsService.upsert(snapshot)
    try {
      stateService.forceVersionMismatchForTests(true)
      assertThat(tabsService.resolveFromPath(snapshot.tabKey.toPath())).isNull()

      stateService.forceVersionMismatchForTests(false)
      assertThat(tabsService.resolveFromPath(snapshot.tabKey.toPath())).isInstanceOf(AgentChatTabResolution.Resolved::class.java)
    }
    finally {
      stateService.forceVersionMismatchForTests(false)
      tabsService.forget(snapshot.tabKey)
    }
  }

  @Test
  fun testResolveFromPathRestoresMultiStepInitialMessageProgress(): Unit = timeoutRunBlocking {
    val tabsService = service<AgentChatTabsService>()
    val steps = listOf(
      AgentInitialMessageDispatchStep(text = "step one"),
      AgentInitialMessageDispatchStep(text = "step two"),
    )
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:multi-step-restore",
      threadId = "multi-step-restore",
      threadTitle = "Multi-step restore",
      subAgentId = null,
      shellCommand = codexCommand,
      initialMessageDispatchSteps = steps,
      initialMessageDispatchStepIndex = 1,
      initialMessageToken = "token-multi-step-restore",
      initialMessageSent = false,
    )
    tabsService.upsert(snapshot)
    try {
      val restored = tabsService.resolveFromPath(snapshot.tabKey.toPath()) as AgentChatTabResolution.Resolved
      assertThat(restored.snapshot.runtime.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
      assertThat(restored.snapshot.runtime.initialMessageDispatchStepIndex).isEqualTo(1)
      assertThat(restored.snapshot.runtime.initialMessageToken).isEqualTo("token-multi-step-restore")
      assertThat(restored.snapshot.runtime.initialMessageSent).isFalse()

      val file = checkNotNull(runInUi {
        agentChatVirtualFileSystem().findFileByPath(snapshot.tabKey.toPath())
      }) as AgentChatVirtualFile
      assertThat(file.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
      assertThat(file.initialMessageDispatchStepIndex).isEqualTo(1)
      assertThat(file.initialComposedMessage).isEqualTo("step two")
      assertThat(file.initialMessageToken).isEqualTo("token-multi-step-restore")
      assertThat(file.initialMessageSent).isFalse()
    }
    finally {
      tabsService.forget(snapshot.tabKey)
    }
  }

  @Test
  fun testFirstWriteAfterVersionMismatchPurgesLegacyEntries(): Unit = timeoutRunBlocking {
    val tabsService = service<AgentChatTabsService>()
    val stateService = service<AgentChatTabsStateService>()
    val legacySnapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:legacy-entry",
      threadId = "legacy-entry",
      threadTitle = "Legacy",
      subAgentId = null,
      shellCommand = codexCommand,
    )
    val newSnapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:new-entry",
      threadId = "new-entry",
      threadTitle = "New",
      subAgentId = null,
      shellCommand = codexCommand,
    )
    tabsService.upsert(legacySnapshot)
    try {
      stateService.forceVersionMismatchForTests(true)
      tabsService.upsert(newSnapshot)

      stateService.forceVersionMismatchForTests(false)
      assertThat(tabsService.load(legacySnapshot.tabKey.value)).isNull()
      assertThat(tabsService.load(newSnapshot.tabKey.value)).isNotNull
    }
    finally {
      stateService.forceVersionMismatchForTests(false)
      tabsService.forget(legacySnapshot.tabKey)
      tabsService.forget(newSnapshot.tabKey)
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
      LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
        override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
          return !message.startsWith("Failed to initialize Agent Chat terminal tab for") || t?.message != "boom"
        }
      }) {
        AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, RuntimeException("boom"))
      }

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
    shellEnvVariables: Map<String, String> = emptyMap(),
    startupShellCommandOverride: List<String>? = null,
    startupShellEnvOverride: Map<String, String>? = null,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
    pendingCreatedAtMs: Long? = null,
    pendingFirstInputAtMs: Long? = null,
    pendingLaunchMode: String? = null,
    initialComposedMessage: String? = null,
    postStartDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList(),
    initialMessageToken: String? = null,
  ) {
    val effectivePostStartDispatchSteps = postStartDispatchSteps.ifEmpty {
      initialComposedMessage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { message -> listOf(AgentInitialMessageDispatchStep(text = message)) }
        .orEmpty()
    }
    val initialMessageDispatchPlan = AgentInitialMessageDispatchPlan(
      startupLaunchSpecOverride = startupShellCommandOverride?.let { command ->
        AgentSessionTerminalLaunchSpec(command = command, envVariables = startupShellEnvOverride.orEmpty())
      },
      postStartDispatchSteps = effectivePostStartDispatchSteps,
      initialMessageToken = initialMessageToken,
    )
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
    )
    waitForCondition(timeoutMs = 10_000) {
      openedChatFiles().any { file ->
        file.threadIdentity == threadIdentity &&
        file.subAgentId == subAgentId &&
        file.threadId == threadId &&
        file.threadTitle == threadTitle &&
        file.shellCommand == shellCommand
      }
    }
  }

  private fun codexPlanDispatchSteps(prompt: String): List<AgentInitialMessageDispatchStep> {
    return listOf(
      AgentInitialMessageDispatchStep(
        text = "/plan",
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
        completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
      ),
      AgentInitialMessageDispatchStep(
        text = prompt,
        timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      ),
    )
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

  private suspend fun <T> runInUi(action: suspend () -> T): T {
    return withContext(Dispatchers.UiWithModelAccess) {
      action()
    }
  }

  private fun rebindTarget(
    threadIdentity: String,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
    provider: AgentSessionProvider = AgentSessionProvider.CODEX,
    projectPath: String = this.projectPath,
  ): AgentChatTabRebindTarget {
    return AgentChatTabRebindTarget(
      projectPath = projectPath,
      provider = provider,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      threadActivity = threadActivity,
    )
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

private fun resolvedCodexResumeCommand(threadId: String): List<String> {
  return listOf("codex", "-c", "check_for_update_on_startup=false", "resume", threadId)
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
