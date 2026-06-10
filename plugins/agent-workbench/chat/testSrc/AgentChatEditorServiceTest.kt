package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
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
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatEditorServiceTest {
  companion object {
    private val CUSTOM_AGENT_CHAT_EDITOR_KEY: Key<Boolean> = Key.create("agent.workbench.chat.test.customEditor")

    @Volatile
    private var customFileEditorFactory: ((Project, AgentChatVirtualFile) -> FileEditor)? = null
  }

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project get() = projectFixture.get()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val dedicatedProjectFixture = projectFixture(openAfterCreation = true)
  private val dedicatedProject get() = dedicatedProjectFixture.get()
  private val dedicatedFileEditorManagerFixture = dedicatedProjectFixture.fileEditorManagerFixture()

  private val projectPath = "/work/project-a"
  private val codexCommand = listOf("codex", "resume", "thread-1")
  private val claudeCommand = listOf("claude", "--resume", "session-1")

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    runInUi {
      fileEditorManagerFixture.get()
      dedicatedFileEditorManagerFixture.get()
      FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(
        TestChatFileEditorProvider(),
        LoadingOrder.FIRST,
        project,
      )
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    customFileEditorFactory = null
    service<AgentSessionThreadPresentationModel>().clearForTests()
    withTimeout(30.seconds) {
      project.waitForSmartMode()
      dedicatedProject.waitForSmartMode()
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

    val snapshot = file.toSnapshot()
    assertThat(snapshot.runtime.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(snapshot.runtime.initialMessageDispatchStepIndex).isZero()
    assertThat(snapshot.runtime.initialMessageToken).isEqualTo("multi-step-new-token")
    assertThat(snapshot.runtime.initialMessageSent).isFalse()
  }

  @Test
  fun testNewTabStartupCommandOverrideKeepsRestoreBackupInitialMessageMetadata(): Unit = timeoutRunBlocking {
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
    assertThat(file.initialComposedMessage).isEqualTo("-draft prompt\nsecond line")
    assertThat(file.initialMessageToken).isEqualTo("startup-token-1")
    assertThat(file.initialMessageSent).isFalse()
  }

  @Test
  fun testNewTabStartupLaunchSpecOverrideIsTransient(): Unit = timeoutRunBlocking {
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

    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec.command).containsExactly("codex", "--", "-draft with env")
    assertThat(startupLaunchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/custom/bin", "DISABLE_AUTOUPDATER" to "1"))

    assertThat(file.consumeStartupLaunchSpecOverride()).isNull()
  }

  @Test
  fun testNewTabPreservesTerminalDefaultShellStartupLaunchSpec(): Unit = timeoutRunBlocking {
    val preallocatedSessionId = "terminal-session-1"
    val terminalLaunchSpec = AgentSessionTerminalLaunchSpec(
      command = emptyList(),
      envVariables = mapOf("A" to "B"),
      useTerminalDefaultShell = true,
      preallocatedSessionId = preallocatedSessionId,
    )
    openChatInModal(
      threadIdentity = "terminal:$preallocatedSessionId",
      shellCommand = emptyList(),
      shellEnvVariables = mapOf("IGNORED" to "1"),
      threadId = preallocatedSessionId,
      threadTitle = "Terminal",
      subAgentId = null,
      newSessionProvider = AgentSessionProvider.TERMINAL,
      newSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
      startupLaunchSpec = terminalLaunchSpec,
    )

    val file = openedChatFiles().single()
    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec).isEqualTo(terminalLaunchSpec)
  }

  @Test
  fun testExistingTabIgnoresStartupCommandOverrideAndKeepsInitialMessageMetadata(): Unit = timeoutRunBlocking {
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
    assertThat(file.initialComposedMessage).isEqualTo("Send through open-tab flow")
    assertThat(file.initialMessageToken).isEqualTo("startup-token-2")
    assertThat(file.initialMessageSent).isFalse()
    assertThat(file.consumeStartupLaunchSpecOverride()).isNull()
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

    val snapshot = file.toSnapshot()
    assertThat(snapshot.runtime.initialMessageDispatchSteps).containsExactlyElementsOf(steps)
    assertThat(snapshot.runtime.initialMessageDispatchStepIndex).isZero()
    assertThat(snapshot.runtime.initialMessageToken).isEqualTo("existing-multi-step-token")
    assertThat(snapshot.runtime.initialMessageSent).isFalse()
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
        editor.showComponentForTests()
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
    waitForCondition { terminalTabs.createCalls == 1 }
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

    assertThat(file.toSnapshot().runtime.initialMessageSent).isTrue()
  }

  @Test
  fun deferredTabStateIsNotPersistedUntilExplicitlyPersisted(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-deferred-start",
      shellCommand = codexCommand,
      threadId = "thread-deferred-start",
      threadTitle = "Deferred start thread",
      subAgentId = null,
      persistSnapshot = false,
      deferredStartState = AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = "Preparing merge resolution",
        message = "Still preparing conflicts",
      ),
    )

    val file = openedChatFiles().single()
    val tabsService = service<AgentChatTabsService>()
    assertThat(tabsService.load(file.tabKey)).isNull()

    tabsService.upsert(file.toSnapshot())

    assertThat(tabsService.load(file.tabKey)).isNotNull()
  }

  @Test
  fun deferredWaitingTabKeepsReadyActivityUntilStartDecision(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-deferred-ready",
      shellCommand = codexCommand,
      threadId = "",
      threadTitle = "Deferred waiting thread",
      subAgentId = null,
      persistSnapshot = false,
      deferredStartState = AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = "Preparing merge resolution",
        message = "Still preparing conflicts",
      ),
    )

    val file = openedChatFiles().single()
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.READY)
    assertThat(file.toSnapshot().runtime.threadActivity).isEqualTo(AgentThreadActivity.READY)
  }

  @Test
  fun concreteNewSessionTabPersistsExplicitStartupIntent(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:a174b4df-e942-49fe-bb30-8b5f8e7f4857",
      shellCommand = listOf("claude", "--session-id", "a174b4df-e942-49fe-bb30-8b5f8e7f4857"),
      threadId = "a174b4df-e942-49fe-bb30-8b5f8e7f4857",
      threadTitle = "New Claude thread",
      subAgentId = null,
      newSessionProvider = AgentSessionProvider.CLAUDE,
      newSessionLaunchMode = AgentSessionLaunchMode.YOLO,
    )

    val file = openedChatFiles().single()
    assertThat(file.startupIntent()).isEqualTo(
      AgentChatStartupIntent.NewSession(
        provider = AgentSessionProvider.CLAUDE,
        launchMode = AgentSessionLaunchMode.YOLO,
      )
    )
  }

  @Test
  fun deferredConcreteNewSessionTabPersistsExplicitStartupIntentWhenStarted(): Unit = timeoutRunBlocking {
    val file = AgentChatVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CLAUDE:c9de00bb-4587-438a-99cc-e8467b4eeb10",
      shellCommand = listOf("claude", "--session-id", "c9de00bb-4587-438a-99cc-e8467b4eeb10"),
      threadId = "c9de00bb-4587-438a-99cc-e8467b4eeb10",
      threadTitle = "Deferred Claude thread",
      subAgentId = null,
    )
    file.updateRestoreOnRestart(false)
    file.updateDeferredStartState(
      AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = "Preparing Claude thread",
      )
    )

    updateAgentChatDeferredStartState(
      project = project,
      file = file,
      deferredStartState = AgentChatDeferredStartState(AgentChatDeferredStartPhase.READY_TO_START, title = ""),
      newSessionProvider = AgentSessionProvider.CLAUDE,
      newSessionLaunchMode = AgentSessionLaunchMode.YOLO,
      persistSnapshot = true,
    )

    assertThat(file.startupIntent()).isEqualTo(
      AgentChatStartupIntent.NewSession(
        provider = AgentSessionProvider.CLAUDE,
        launchMode = AgentSessionLaunchMode.YOLO,
      )
    )
  }

  @Test
  fun deferredSuccessNoStartTabIsExcludedFromPendingCollections(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-deferred-success",
      shellCommand = codexCommand,
      threadId = "",
      threadTitle = "Deferred merge thread",
      subAgentId = null,
      persistSnapshot = false,
      deferredStartState = AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = "Preparing merge resolution",
        message = "Still preparing conflicts",
      ),
    )

    val file = openedChatFiles().single()
    assertThat(collectOpenPendingCodexTabsByPath()[projectPath].orEmpty().map { it.pendingTabKey }).containsExactly(file.tabKey)
    assertThat(collectOpenPendingAgentChatProjectPaths()).containsExactly(projectPath)

    file.updateDeferredStartState(
      AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.SUCCESS_NO_START,
        title = "Merge conflicts resolved",
        message = "All conflicts were resolved automatically.",
      )
    )

    assertThat(collectOpenPendingCodexTabsByPath()).isEmpty()
    assertThat(collectOpenPendingAgentChatProjectPaths()).isEmpty()
  }

  @Test
  fun deferredFailureNoStartTabIsExcludedFromPendingCollections(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-deferred-failure",
      shellCommand = codexCommand,
      threadId = "",
      threadTitle = "Deferred merge thread",
      subAgentId = null,
      persistSnapshot = false,
      deferredStartState = AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = "Preparing merge resolution",
        message = "Still preparing conflicts",
      ),
    )

    val file = openedChatFiles().single()
    assertThat(collectOpenPendingCodexTabsByPath()[projectPath].orEmpty().map { it.pendingTabKey }).containsExactly(file.tabKey)
    assertThat(collectOpenPendingAgentChatProjectPaths()).containsExactly(projectPath)

    file.updateDeferredStartState(
      AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.FAILURE_NO_START,
        title = "Couldn't prepare merge resolution",
        message = "The merge session could not be prepared for agent-assisted resolution.",
      )
    )

    assertThat(collectOpenPendingCodexTabsByPath()).isEmpty()
    assertThat(collectOpenPendingAgentChatProjectPaths()).isEmpty()
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
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Renamed thread",
      subAgentId = null,
    )
    waitForCondition {
      val file = openedChatFiles().singleOrNull() ?: return@waitForCondition false
      file.threadIdentity == "CODEX:thread-1" &&
      file.threadId == "thread-1" &&
      file.threadTitle == "Renamed thread" &&
      editorTabTitle(file) == "Renamed thread"
    }

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
    val updatedTabs = publishThreadPresentation(
      file = file,
      title = "Renamed by source update",
      activity = AgentThreadActivity.UNREAD,
    )
    assertThat(updatedTabs).isEqualTo(1)

    assertThat(file.threadTitle).isEqualTo("Renamed by source update")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(editorTabTitle(file)).isEqualTo("Renamed by source update")

    val unchangedTabs = publishThreadPresentation(
      file = file,
      title = "Renamed by source update",
      activity = AgentThreadActivity.UNREAD,
    )
    assertThat(unchangedTabs).isEqualTo(0)
  }

  @Test
  fun testUpdateOpenChatTabPresentationNormalizesIncomingProjectPaths(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Initial title",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val updatedTabs = publishThreadPresentation(
      file = file,
      path = "${file.projectPath}/",
      title = "Renamed by normalized source update",
      activity = AgentThreadActivity.UNREAD,
    )
    assertThat(updatedTabs).isEqualTo(1)

    assertThat(file.threadTitle).isEqualTo("Renamed by normalized source update")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(editorTabTitle(file)).isEqualTo("Renamed by normalized source update")

    val unchangedTabs = publishThreadPresentation(
      file = file,
      path = "${file.projectPath}/",
      title = "Renamed by normalized source update",
      activity = AgentThreadActivity.UNREAD,
    )
    assertThat(unchangedTabs).isEqualTo(0)
  }

  @Test
  fun testProjectColorChangeRefreshesMatchingChatTabPresentationOnlyInDedicatedFrames(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-source-frame",
      shellCommand = listOf("codex", "resume", "thread-source-frame"),
      threadId = "thread-source-frame",
      threadTitle = "Source frame thread",
      subAgentId = null,
      targetProject = project,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-dedicated-frame",
      shellCommand = listOf("codex", "resume", "thread-dedicated-frame"),
      threadId = "thread-dedicated-frame",
      threadTitle = "Dedicated frame thread",
      subAgentId = null,
      targetProject = dedicatedProject,
    )
    val otherProjectPath = "/work/project-b"
    openChatInModal(
      sourceProjectPath = otherProjectPath,
      threadIdentity = "CODEX:thread-other-project",
      shellCommand = listOf("codex", "resume", "thread-other-project"),
      threadId = "thread-other-project",
      threadTitle = "Other project thread",
      subAgentId = null,
      targetProject = dedicatedProject,
    )

    val sourceFrameFile = openedChatFiles(project).single()
    val dedicatedFrameFile = openedChatFiles(dedicatedProject).single { it.projectPath == projectPath }
    val otherProjectFile = openedChatFiles(dedicatedProject).single { it.projectPath == otherProjectPath }
    val updatedProjects = ArrayList<Project>()
    val updatedFiles = ArrayList<AgentChatVirtualFile>()
    val updatedPresentations = runInUi {
      refreshOpenAgentChatTabColors(
        projects = arrayOf(project, dedicatedProject),
        sourceProjectPaths = setOf(projectPath),
        isDedicatedProject = { candidate -> candidate === dedicatedProject },
        updateFilePresentation = { manager, file ->
          updatedProjects.add(manager.project)
          updatedFiles.add(file)
        },
      )
    }

    assertThat(updatedPresentations).isEqualTo(1)
    assertThat(updatedProjects).containsExactly(dedicatedProject)
    assertThat(updatedFiles).containsExactly(dedicatedFrameFile)
    assertThat(updatedFiles).doesNotContain(sourceFrameFile)
    assertThat(updatedFiles).doesNotContain(otherProjectFile)
  }

  @Test
  fun testUpdateOpenChatTabPresentationDoesNotOverrideSubAgentTitle(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Parent thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "sub-1"),
      threadId = "sub-1",
      threadTitle = "Sub-agent label",
      subAgentId = "sub-1",
    )

    val updatedTabs = publishThreadPresentation(
      file = openedChatFiles().first { it.subAgentId == null },
      title = "Renamed parent",
      activity = null,
    )
    assertThat(updatedTabs).isEqualTo(1)

    val files = openedChatFiles()
    assertThat(files.first { it.subAgentId == null }.threadTitle).isEqualTo("Renamed parent")
    assertThat(files.first { it.subAgentId == "sub-1" }.threadTitle).isEqualTo("Sub-agent label")
  }

  @Test
  fun testUpdateOpenChatTabPresentationUpdatesSubAgentActivityWithoutChangingTitle(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Parent thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "sub-1"),
      threadId = "sub-1",
      threadTitle = "Sub-agent label",
      subAgentId = "sub-1",
    )

    val updatedTabs = publishThreadPresentation(
      file = openedChatFiles().first { it.subAgentId == null },
      title = "Renamed parent",
      activity = AgentThreadActivity.UNREAD,
    )
    assertThat(updatedTabs).isEqualTo(2)

    val files = openedChatFiles()
    assertThat(files.first { it.subAgentId == null }.threadTitle).isEqualTo("Renamed parent")
    assertThat(files.first { it.subAgentId == null }.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(files.first { it.subAgentId == "sub-1" }.threadTitle).isEqualTo("Sub-agent label")
    assertThat(files.first { it.subAgentId == "sub-1" }.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testAgentChatFileEditorUsesStableEditorKindName(): Unit = timeoutRunBlocking {
    val file = AgentChatVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-editor-name",
      shellCommand = codexCommand,
      threadId = "thread-editor-name",
      threadTitle = "Initial title",
      subAgentId = null,
    )

    val editor = runInUi {
      AgentChatFileEditor(
        project = project,
        file = file,
        terminalTabs = EditorServiceFakeAgentChatTerminalTabs(),
      )
    }
    try {
      assertThat(editor.name).isEqualTo(AgentChatBundle.message("chat.filetype.name"))
      file.updateBootstrapThreadTitle("Renamed title")
      assertThat(editor.name).isEqualTo(AgentChatBundle.message("chat.filetype.name"))
    }
    finally {
      Disposer.dispose(editor)
    }
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
    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CODEX:thread-3",
              threadId = "thread-3",
              threadTitle = "Recovered thread",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    assertThat(file.threadIdentity).isEqualTo("CODEX:thread-3")
    assertThat(file.threadId).isEqualTo("thread-3")
    assertThat(file.threadTitle).isEqualTo("Recovered thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(editorTabTitle(file)).isEqualTo("Recovered thread")
    assertThat(editorTabTooltip(file)).isEqualTo("Recovered thread")

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
  fun testRebindOpenPendingChatTabUsesAlreadyPublishedConcreteThreadPresentation(): Unit = timeoutRunBlocking {
    val targetThreadId = "019eb1ec-416a-7572-9578-abac8b6181ce"
    val staleTitle = "Thread ${targetThreadId.take(8)}"
    val sharedTitle = "what idea theme do we use as source for pi?"
    openChatInModal(
      threadIdentity = "CODEX:new-shared-presentation",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = staleTitle,
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    assertThat(publishThreadPresentation(
      path = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = targetThreadId,
      title = sharedTitle,
      activity = AgentThreadActivity.PROCESSING,
    )).isEqualTo(0)

    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CODEX:$targetThreadId",
              threadId = targetThreadId,
              threadTitle = staleTitle,
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)
    assertThat(file.threadIdentity).isEqualTo("CODEX:$targetThreadId")
    assertThat(file.threadId).isEqualTo(targetThreadId)
    assertThat(file.bootstrapThreadTitle).isEqualTo(sharedTitle)
    assertThat(file.threadTitle).isEqualTo(sharedTitle)
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(editorTabTitle(file)).isEqualTo(sharedTitle)
    assertThat(editorTabTooltip(file)).isEqualTo(sharedTitle)
  }

  @Test
  fun testRebindOpenPendingCodexTabKeepsConcreteNewThreadAnchorTabOpen(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-old",
      shellCommand = listOf("codex", "resume", "thread-old"),
      threadId = "thread-old",
      threadTitle = "Previous thread",
      subAgentId = null,
    )

    val concreteFile = openedChatFiles().single()
    concreteFile.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(concreteFile.toSnapshot())

    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
      pendingCreatedAtMs = 1_100L,
      pendingLaunchMode = "standard",
    )

    val pendingFile = openedChatFiles().single { file -> file.threadIdentity == "CODEX:new-1" }
    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = pendingFile.tabKey,
            pendingThreadIdentity = pendingFile.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CODEX:thread-new",
              threadId = "thread-new",
              threadTitle = "Recovered thread",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    val files = openedChatFiles()
    assertThat(files.map { file -> file.threadIdentity })
      .containsExactlyInAnyOrder("CODEX:thread-old", "CODEX:thread-new")

    val previousThreadFile = files.single { file -> file.tabKey == concreteFile.tabKey }
    assertThat(previousThreadFile.threadIdentity).isEqualTo("CODEX:thread-old")
    assertThat(previousThreadFile.threadId).isEqualTo("thread-old")
    assertThat(previousThreadFile.threadTitle).isEqualTo("Previous thread")
    assertThat(previousThreadFile.newThreadRebindRequestedAtMs).isEqualTo(1_000L)

    val reboundFile = files.single { file -> file.tabKey == pendingFile.tabKey }
    assertThat(reboundFile.threadIdentity).isEqualTo("CODEX:thread-new")
    assertThat(reboundFile.threadId).isEqualTo("thread-new")
    assertThat(reboundFile.threadTitle).isEqualTo("Recovered thread")
    assertThat(reboundFile.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testRejectPendingCodexRebindTargetThatStillPointsToNewThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CODEX:new-2",
              threadId = "new-2",
              threadTitle = "New thread",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB)
    assertThat(file.threadIdentity).isEqualTo("CODEX:new-1")
    assertThat(file.threadTitle).isEqualTo("New thread")
    assertThat(editorTabTitle(file)).isEqualTo("New thread")
    assertThat(editorTabTooltip(file)).isEqualTo("New thread")
  }

  @Test
  fun testCollectOpenPendingCodexTabsByPathIncludesPendingMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending thread",
      subAgentId = null,
      pendingCreatedAtMs = 1_000L,
      pendingFirstInputAtMs = 1_250L,
      pendingLaunchMode = "yolo",
    )

    val pendingFile = openedChatFiles().single()
    val pendingTabsByPath = collectOpenPendingCodexTabsByPath()
    val pendingSnapshot = pendingTabsByPath[projectPath].orEmpty().single()
    assertThat(pendingSnapshot.projectPath).isEqualTo(projectPath)
    assertThat(pendingSnapshot.pendingTabKey).isEqualTo(pendingFile.tabKey)
    assertThat(pendingSnapshot.pendingThreadIdentity).isEqualTo("CODEX:new-1")
    assertThat(pendingSnapshot.pendingCreatedAtMs).isEqualTo(1_000L)
    assertThat(pendingSnapshot.pendingFirstInputAtMs).isEqualTo(1_250L)
    assertThat(pendingSnapshot.pendingLaunchMode).isEqualTo("yolo")
  }

  @Test
  fun testCollectOpenPendingClaudeTabsByPathIncludesPendingMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = claudeCommand,
      threadId = "",
      threadTitle = "Pending Claude thread",
      subAgentId = null,
      pendingCreatedAtMs = 2_000L,
      pendingFirstInputAtMs = 2_250L,
      pendingLaunchMode = "standard",
    )

    val pendingFile = openedChatFiles().single()
    val pendingTabsByPath = collectOpenPendingAgentChatTabsByPath(AgentSessionProvider.CLAUDE)
    val pendingSnapshot = pendingTabsByPath[projectPath].orEmpty().single()
    assertThat(pendingSnapshot.projectPath).isEqualTo(projectPath)
    assertThat(pendingSnapshot.pendingTabKey).isEqualTo(pendingFile.tabKey)
    assertThat(pendingSnapshot.pendingThreadIdentity).isEqualTo("CLAUDE:new-1")
    assertThat(pendingSnapshot.pendingCreatedAtMs).isEqualTo(2_000L)
    assertThat(pendingSnapshot.pendingFirstInputAtMs).isEqualTo(2_250L)
    assertThat(pendingSnapshot.pendingLaunchMode).isEqualTo("standard")
  }

  @Test
  fun testRebindOpenPendingClaudeChatTabToConcreteThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = claudeCommand,
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val rebindReport = rebindOpenPendingAgentChatTabs(
      provider = AgentSessionProvider.CLAUDE,
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CLAUDE:thread-3",
              threadId = "thread-3",
              threadTitle = "Recovered Claude thread",
              threadActivity = AgentThreadActivity.UNREAD,
              provider = AgentSessionProvider.CLAUDE,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    assertThat(file.threadIdentity).isEqualTo("CLAUDE:thread-3")
    assertThat(file.threadId).isEqualTo("thread-3")
    assertThat(file.threadTitle).isEqualTo("Recovered Claude thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(editorTabTitle(file)).isEqualTo("Recovered Claude thread")
    assertThat(editorTabTooltip(file)).isEqualTo("Recovered Claude thread")

    openChatInModal(
      threadIdentity = "CLAUDE:thread-3",
      shellCommand = listOf("claude", "--resume", "thread-3"),
      threadId = "thread-3",
      threadTitle = "Recovered Claude thread",
      subAgentId = null,
    )
    assertThat(openedChatFiles()).hasSize(1)
  }

  @Test
  fun testRejectPendingClaudeRebindTargetThatStillPointsToNewThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = claudeCommand,
      threadId = "",
      threadTitle = "New thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val rebindReport = rebindOpenPendingAgentChatTabs(
      provider = AgentSessionProvider.CLAUDE,
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = rebindTarget(
              threadIdentity = "CLAUDE:new-2",
              threadId = "new-2",
              threadTitle = "New thread",
              threadActivity = AgentThreadActivity.READY,
              provider = AgentSessionProvider.CLAUDE,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB)
    assertThat(file.threadIdentity).isEqualTo("CLAUDE:new-1")
    assertThat(file.threadTitle).isEqualTo("New thread")
    assertThat(editorTabTitle(file)).isEqualTo("New thread")
    assertThat(editorTabTooltip(file)).isEqualTo("New thread")
  }

  @Test
  fun testCollectOpenConcreteAgentChatThreadIdentitiesByPathSkipsPendingTabs(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadId = "thread-42",
      threadTitle = "Concrete thread",
      subAgentId = null,
    )

    val concreteByPath = collectOpenConcreteAgentChatThreadIdentitiesByPath()
    assertThat(concreteByPath[projectPath]).containsExactly("CODEX:thread-42")
  }

  @Test
  fun testCollectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadId = "thread-42",
      threadTitle = "Concrete thread",
      subAgentId = null,
    )

    val concreteFile = openedChatFiles().single()
    concreteFile.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(concreteFile.toSnapshot())

    val concreteTabsByPath = collectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath()
    val concreteSnapshot = concreteTabsByPath[projectPath].orEmpty().single()
    assertThat(concreteSnapshot.projectPath).isEqualTo(projectPath)
    assertThat(concreteSnapshot.tabKey).isEqualTo(concreteFile.tabKey)
    assertThat(concreteSnapshot.currentThreadIdentity).isEqualTo("CODEX:thread-42")
    assertThat(concreteSnapshot.newThreadRebindRequestedAtMs).isEqualTo(1_000L)
  }

  @Test
  fun testRebindOpenConcreteCodexTabToNewThread(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Original thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    file.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(file.toSnapshot())

    val rebindReport = rebindOpenConcreteCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatConcreteTabRebindRequest(
            tabKey = file.tabKey,
            currentThreadIdentity = file.threadIdentity,
            newThreadRebindRequestedAtMs = 1_000L,
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "New thread",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.requestedBindings).isEqualTo(1)
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatConcreteTabRebindStatus.REBOUND)
    assertThat(file.threadIdentity).isEqualTo("CODEX:thread-2")
    assertThat(file.threadId).isEqualTo("thread-2")
    assertThat(file.threadTitle).isEqualTo("New thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(file.newThreadRebindRequestedAtMs).isNull()
  }

  @Test
  fun testRebindOpenConcreteCodexTabUsesAlreadyPublishedNewThreadPresentation(): Unit = timeoutRunBlocking {
    val targetThreadId = "thread-shared-concrete"
    val staleTitle = "Thread shared"
    val sharedTitle = "Parsed concrete new thread title"
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Original thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    file.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(file.toSnapshot())
    assertThat(publishThreadPresentation(
      path = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = targetThreadId,
      title = sharedTitle,
      activity = AgentThreadActivity.PROCESSING,
    )).isEqualTo(0)

    val rebindReport = rebindOpenConcreteCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatConcreteTabRebindRequest(
            tabKey = file.tabKey,
            currentThreadIdentity = file.threadIdentity,
            newThreadRebindRequestedAtMs = 1_000L,
            target = rebindTarget(
              threadIdentity = "CODEX:$targetThreadId",
              threadId = targetThreadId,
              threadTitle = staleTitle,
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatConcreteTabRebindStatus.REBOUND)
    assertThat(file.threadIdentity).isEqualTo("CODEX:$targetThreadId")
    assertThat(file.threadId).isEqualTo(targetThreadId)
    assertThat(file.bootstrapThreadTitle).isEqualTo(sharedTitle)
    assertThat(file.threadTitle).isEqualTo(sharedTitle)
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(file.newThreadRebindRequestedAtMs).isNull()
    assertThat(editorTabTitle(file)).isEqualTo(sharedTitle)
    assertThat(editorTabTooltip(file)).isEqualTo(sharedTitle)
  }

  @Test
  fun testRebindOpenConcreteCodexTabsFailsWhenTargetIsAlreadyOpen(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Original thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Already open",
      subAgentId = null,
    )

    val concreteTab = openedChatFiles().first { it.threadIdentity == "CODEX:thread-1" }
    concreteTab.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(concreteTab.toSnapshot())

    val rebindReport = rebindOpenConcreteCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatConcreteTabRebindRequest(
            tabKey = concreteTab.tabKey,
            currentThreadIdentity = "CODEX:thread-1",
            newThreadRebindRequestedAtMs = 1_000L,
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "Should not replace",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatConcreteTabRebindStatus.TARGET_ALREADY_OPEN)
    assertThat(concreteTab.threadIdentity).isEqualTo("CODEX:thread-1")
    assertThat(concreteTab.newThreadRebindRequestedAtMs).isEqualTo(1_000L)
  }

  @Test
  fun testRebindOpenConcreteCodexTabRejectsStaleAnchorTimestamp(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Original thread",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    file.updateNewThreadRebindRequestedAtMs(1_000L)
    service<AgentChatTabsService>().upsert(file.toSnapshot())
    val staleSnapshot = collectOpenConcreteCodexTabsAwaitingNewThreadRebindByPath().getValue(projectPath).single()

    file.updateNewThreadRebindRequestedAtMs(2_000L)
    service<AgentChatTabsService>().upsert(file.toSnapshot())

    val rebindReport = rebindOpenConcreteCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatConcreteTabRebindRequest(
            tabKey = staleSnapshot.tabKey,
            currentThreadIdentity = staleSnapshot.currentThreadIdentity,
            newThreadRebindRequestedAtMs = staleSnapshot.newThreadRebindRequestedAtMs,
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "Should not rebind",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatConcreteTabRebindStatus.INVALID_CONCRETE_TAB)
    assertThat(file.threadIdentity).isEqualTo("CODEX:thread-1")
    assertThat(file.newThreadRebindRequestedAtMs).isEqualTo(2_000L)
  }

  @Test
  fun testRebindOpenPendingCodexTabsTargetsOnlyRequestedPendingIdentity(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending one",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:new-2",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending two",
      subAgentId = null,
    )

    val pendingTab = openedChatFiles().first { it.threadIdentity == "CODEX:new-2" }
    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = "CODEX:new-2",
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "Recovered two",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    val filesByIdentity = openedChatFiles().associateBy { it.threadIdentity }
    assertThat(filesByIdentity).containsKey("CODEX:new-1")
    assertThat(filesByIdentity).containsKey("CODEX:thread-2")
    assertThat(filesByIdentity["CODEX:thread-2"]?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testRebindOpenPendingCodexTabsReusesPendingTabWhenConcreteIdentityIsAlreadyOpen(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending one",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Already open",
      subAgentId = null,
    )

    val pendingTab = openedChatFiles().first { it.threadIdentity == "CODEX:new-1" }
    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = "CODEX:new-1",
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "Recovered thread",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
    assertThat(files.single().threadIdentity).isEqualTo("CODEX:thread-2")
    assertThat(files.single().threadId).isEqualTo("thread-2")
    assertThat(files.single().threadTitle).isEqualTo("Recovered thread")
    assertThat(files.single().threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testRebindOpenPendingClaudeTabsReusesPendingTabWhenConcreteIdentityIsAlreadyOpen(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = listOf("claude"),
      threadId = "",
      threadTitle = "Pending one",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CLAUDE:thread-2",
      shellCommand = listOf("claude", "--resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Already open",
      subAgentId = null,
    )

    val pendingTab = openedChatFiles().first { it.threadIdentity == "CLAUDE:new-1" }
    val rebindReport = rebindOpenPendingAgentChatTabs(
      provider = AgentSessionProvider.CLAUDE,
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = "CLAUDE:new-1",
            target = rebindTarget(
              provider = AgentSessionProvider.CLAUDE,
              threadIdentity = "CLAUDE:thread-2",
              threadId = "thread-2",
              threadTitle = "Recovered Claude thread",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    val files = openedChatFiles()
    assertThat(files).hasSize(1)
    assertThat(files.single().threadIdentity).isEqualTo("CLAUDE:thread-2")
    assertThat(files.single().threadId).isEqualTo("thread-2")
    assertThat(files.single().threadTitle).isEqualTo("Recovered Claude thread")
    assertThat(files.single().threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testRebindOpenPendingCodexTabsClosesConcreteTabInAnotherOpenProject(): Unit = timeoutRunBlocking {
    assertRebindOpenPendingTabsClosesConcreteTabInAnotherOpenProject(AgentSessionProvider.CODEX)
  }

  @Test
  fun testRebindOpenPendingClaudeTabsClosesConcreteTabInAnotherOpenProject(): Unit = timeoutRunBlocking {
    assertRebindOpenPendingTabsClosesConcreteTabInAnotherOpenProject(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun testRebindOpenPendingCodexTabsReportsMissingPendingTabKey(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:new-1",
      shellCommand = listOf("codex"),
      threadId = "",
      threadTitle = "Pending one",
      subAgentId = null,
    )

    val rebindReport = rebindOpenPendingCodexTabs(
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = "missing-tab-key",
            pendingThreadIdentity = "CODEX:new-1",
            target = rebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              threadTitle = "Recovered",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.PENDING_TAB_NOT_OPEN)
  }

  @Test
  fun testCollectOpenPendingProjectPathsIncludesAnyPendingProvider(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CLAUDE:new-1",
      shellCommand = claudeCommand,
      threadId = "",
      threadTitle = "Claude pending",
      subAgentId = null,
    )

    assertThat(collectOpenPendingAgentChatProjectPaths()).containsExactly(projectPath)

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
  fun testCollectSelectedChatThreadIdentityUsesSelectedChatTab(): Unit = timeoutRunBlocking {
    val selectionService = project.service<AgentChatTabSelectionService>()

    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "First thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-2",
      shellCommand = listOf("codex", "resume", "thread-2"),
      threadId = "thread-2",
      threadTitle = "Second thread",
      subAgentId = null,
    )

    val firstFile = openedChatFiles().first { it.threadIdentity == "CODEX:thread-1" }
    runInUi {
      FileEditorManager.getInstance(project).openFile(firstFile, true)
    }

    waitForCondition {
      selectionService.selectedChatTab.value == AgentChatTabSelection(
        projectPath = projectPath,
        threadIdentity = "CODEX:thread-1",
        threadId = "thread-1",
        subAgentId = null,
      )
    }

    assertThat(selectionService.selectedChatTab.value).isEqualTo(
      AgentChatTabSelection(
        projectPath = projectPath,
        threadIdentity = "CODEX:thread-1",
        threadId = "thread-1",
        subAgentId = null,
      )
    )
    assertThat(collectSelectedChatThreadIdentity()).isEqualTo(AgentSessionProvider.CODEX to "thread-1")
  }

  @Test
  fun testVirtualFileSystemReusesOpenFileByTabKey(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-reuse-path",
      shellCommand = listOf("codex", "resume", "thread-reuse-path"),
      threadId = "thread-reuse-path",
      threadTitle = "Reuse path",
      subAgentId = null,
    )

    val file = openedChatFiles().single()
    val tabPath = AgentChatTabKey.parse(file.tabKey)!!.toPath()

    val fileSystem = agentChatVirtualFileSystem()
    val resolvedFile = runInUi {
      fileSystem.findFileByPath(tabPath)
    }

    assertThat(resolvedFile).isSameAs(file)
  }

  @Test
  fun testCodexScopedRefreshSignalsEmitNormalizedPaths(): Unit = timeoutRunBlocking {
    val projectPath = "/work/project-scoped-refresh-delayed/"
    val signalWaiter = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        codexScopedRefreshSignals().first()
      }
    }

    notifyCodexScopedRefresh(projectPath)

    val signal = signalWaiter.await()
    assertThat(signal.scopedPaths).containsExactly("/work/project-scoped-refresh-delayed")
    assertThat(signal.activityUpdatesByThreadId).isEmpty()
  }

  @Test
  fun testCodexScopedRefreshSignalsCarryKnownThreadId(): Unit = timeoutRunBlocking {
    val signalWaiter = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        codexScopedRefreshSignals().first()
      }
    }

    notifyAgentChatScopedRefresh(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project-scoped-refresh-thread/",
      threadId = "codex-thread-1",
    )

    val signal = signalWaiter.await()
    assertThat(signal.scopedPaths).containsExactly("/work/project-scoped-refresh-thread")
    assertThat(signal.threadIds).containsExactly("codex-thread-1")
    assertThat(signal.activityUpdatesByThreadId).isEmpty()
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
    val presentationModel = service<AgentSessionThreadPresentationModel>()
    val beforeCleanup = openedChatFiles()
    assertThat(beforeCleanup).hasSize(3)
    beforeCleanup.forEach { tabsService.upsert(it.toSnapshot()) }
    val rootPresentationKey =
      checkNotNull(beforeCleanup.first { it.subAgentId == null && it.threadIdentity == "CODEX:thread-1" }.presentationKeyOrNull())
    presentationModel.updateThread(
      path = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-1",
      title = "Main thread",
      activity = AgentThreadActivity.READY,
    )
    assertThat(presentationModel.resolve(rootPresentationKey)).isNotNull
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
    assertThat(presentationModel.resolve(rootPresentationKey)).isNotNull
    assertThat(tabsService.load(unrelatedTabKey)).isNotNull
  }

  @Test
  fun testArchiveCleanupForSubAgentClosesOnlyMatchingSubAgentTabAndMetadata(): Unit = timeoutRunBlocking {
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = codexCommand,
      threadId = "thread-1",
      threadTitle = "Main thread",
      subAgentId = null,
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "sub-alpha"),
      threadId = "sub-alpha",
      threadTitle = "Alpha",
      subAgentId = "sub-alpha",
    )
    openChatInModal(
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "sub-beta"),
      threadId = "sub-beta",
      threadTitle = "Beta",
      subAgentId = "sub-beta",
    )

    val tabsService = service<AgentChatTabsService>()
    val presentationModel = service<AgentSessionThreadPresentationModel>()
    val beforeCleanup = openedChatFiles()
    beforeCleanup.forEach { tabsService.upsert(it.toSnapshot()) }
    val alphaTabKey = beforeCleanup.first { it.subAgentId == "sub-alpha" }.tabKey
    val betaTabKey = beforeCleanup.first { it.subAgentId == "sub-beta" }.tabKey
    val baseTabKey = beforeCleanup.first { it.subAgentId == null }.tabKey
    val basePresentationKey = checkNotNull(beforeCleanup.first { it.subAgentId == null }.presentationKeyOrNull())
    presentationModel.updateThread(
      path = projectPath,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-1",
      title = "Main thread",
      activity = AgentThreadActivity.READY,
    )

    closeAndForgetAgentChatsForThread(
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-1",
      subAgentId = "sub-alpha",
    )

    val remaining = openedChatFiles()
    assertThat(remaining).hasSize(2)
    assertThat(remaining.map { it.subAgentId }).containsExactlyInAnyOrder(null, "sub-beta")
    assertThat(tabsService.load(alphaTabKey)).isNull()
    assertThat(tabsService.load(baseTabKey)).isNotNull
    assertThat(tabsService.load(betaTabKey)).isNotNull
    assertThat(presentationModel.resolve(basePresentationKey)).isNotNull
  }

  @Test
  fun testValidationFailureDeletesMetadata(): Unit = timeoutRunBlocking {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = "",
      threadIdentity = "CODEX:invalid-project",
      threadId = "invalid-project",
      threadTitle = "Invalid",
      subAgentId = null,
    )
    val tabsService = service<AgentChatTabsService>()
    tabsService.upsert(snapshot)
    try {
      val file = agentChatVirtualFileSystem().getOrCreateFile(snapshot)
      val editor = runInUi {
        AgentChatFileEditorProvider().createEditor(project, file)
      }
      runInUi { editor.selectNotify() }

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
    )
    tabsService.upsert(snapshot)
    try {
      stateService.forceVersionMismatchForTests(true)
      assertThat(tabsService.resolveFromPath(snapshot.tabKey.toPath())).isInstanceOf(AgentChatTabResolution.Unresolved::class.java)

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

      val fileSystem = agentChatVirtualFileSystem()
      val file = checkNotNull(runInUi {
        fileSystem.findFileByPath(snapshot.tabKey.toPath())
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
    )
    val newSnapshot = AgentChatTabSnapshot.create(
      projectHash = project.locationHash,
      projectPath = projectPath,
      threadIdentity = "CODEX:new-entry",
      threadId = "new-entry",
      threadTitle = "New",
      subAgentId = null,
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
        runBlocking {
          AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, RuntimeException("boom"))
        }
      }

      assertThat(tabsService.load(snapshot.tabKey.value)).isNull()
    }
    finally {
      tabsService.forget(snapshot.tabKey)
    }
  }

  private suspend fun openedChatFiles(targetProject: Project = project): List<AgentChatVirtualFile> {
    return runInUi {
      FileEditorManager.getInstance(targetProject).openFiles.filterIsInstance<AgentChatVirtualFile>()
    }
  }

  private suspend fun assertRebindOpenPendingTabsClosesConcreteTabInAnotherOpenProject(provider: AgentSessionProvider) {
    val providerPrefix = providerIdentityPrefix(provider)
    val pendingThreadId = "new-cross-project-${provider.value}"
    val targetThreadId = "thread-cross-project-${provider.value}"
    val pendingIdentity = "$providerPrefix:$pendingThreadId"
    val targetIdentity = "$providerPrefix:$targetThreadId"

    openChatInModal(
      threadIdentity = pendingIdentity,
      shellCommand = newThreadCommand(provider),
      threadId = "",
      threadTitle = "Pending ${provider.value}",
      subAgentId = null,
      pendingCreatedAtMs = 1_000L,
      pendingLaunchMode = "standard",
      targetProject = project,
    )
    openChatInModal(
      threadIdentity = targetIdentity,
      shellCommand = resumeThreadCommand(provider, targetThreadId),
      threadId = targetThreadId,
      threadTitle = "Already open ${provider.value}",
      subAgentId = null,
      targetProject = dedicatedProject,
    )

    val pendingTab = openedChatFiles(project).single { file -> file.threadIdentity == pendingIdentity }
    val rebindReport = rebindOpenPendingAgentChatTabs(
      provider = provider,
      requestsByProjectPath = mapOf(
        projectPath to listOf(
          AgentChatPendingTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = pendingIdentity,
            target = rebindTarget(
              provider = provider,
              threadIdentity = targetIdentity,
              threadId = targetThreadId,
              threadTitle = "Recovered ${provider.value}",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingTabRebindStatus.REBOUND)

    val sourceProjectFiles = openedChatFiles(project)
    val dedicatedProjectFiles = openedChatFiles(dedicatedProject)
    assertThat(sourceProjectFiles).hasSize(1)
    assertThat(dedicatedProjectFiles).isEmpty()

    val reboundFile = sourceProjectFiles.single()
    assertThat(reboundFile.threadIdentity).isEqualTo(targetIdentity)
    assertThat(reboundFile.threadId).isEqualTo(targetThreadId)
    assertThat(reboundFile.threadTitle).isEqualTo("Recovered ${provider.value}")
    assertThat(reboundFile.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  private suspend fun openChatInModal(
    sourceProjectPath: String = projectPath,
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
    newSessionProvider: AgentSessionProvider? = null,
    newSessionLaunchMode: AgentSessionLaunchMode? = null,
    initialComposedMessage: String? = null,
    postStartDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList(),
    initialMessageToken: String? = null,
    persistSnapshot: Boolean = true,
    deferredStartState: AgentChatDeferredStartState? = null,
    targetProject: Project = project,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec? = null,
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
      project = targetProject,
      projectPath = sourceProjectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
      newSessionProvider = newSessionProvider,
      newSessionLaunchMode = newSessionLaunchMode,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      persistSnapshot = persistSnapshot,
      deferredStartState = deferredStartState,
      startupLaunchSpec = startupLaunchSpec,
    )
    waitForCondition(timeoutMs = 10_000) {
      openedChatFiles(targetProject).any { file ->
        file.threadIdentity == threadIdentity &&
        file.subAgentId == subAgentId &&
        file.threadId == threadId &&
        file.threadTitle == threadTitle
      }
    }
  }

  private fun providerIdentityPrefix(provider: AgentSessionProvider): String {
    return when (provider) {
      AgentSessionProvider.CODEX -> "CODEX"
      AgentSessionProvider.CLAUDE -> "CLAUDE"
      else -> error("Unsupported test provider: $provider")
    }
  }

  private fun newThreadCommand(provider: AgentSessionProvider): List<String> {
    return when (provider) {
      AgentSessionProvider.CODEX -> listOf("codex")
      AgentSessionProvider.CLAUDE -> listOf("claude")
      else -> error("Unsupported test provider: $provider")
    }
  }

  private fun resumeThreadCommand(provider: AgentSessionProvider, threadId: String): List<String> {
    return when (provider) {
      AgentSessionProvider.CODEX -> listOf("codex", "resume", threadId)
      AgentSessionProvider.CLAUDE -> listOf("claude", "--resume", threadId)
      else -> error("Unsupported test provider: $provider")
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
      AgentChatEditorTabTitleProvider().getEditorTabTooltipHtml(project, file)?.toString()
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
      val chatFile = file as AgentChatVirtualFile
      return customFileEditorFactory?.invoke(project, chatFile)
             ?: LightweightTestFileEditor(file, editorName = "AgentChatTestEditor")
    }

    override fun getEditorTypeId(): String = "agent.workbench-chat-editor-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}

private class EditorServiceFakeAgentChatTerminalTabs : AgentChatTerminalTabs {
  var createCalls: Int = 0
  val tab = EditorServiceFakeAgentChatTerminalTab()

  override fun createTab(
    project: Project,
    file: AgentChatVirtualFile,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentChatTerminalTab {
    createCalls++
    return tab
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    (tab as? EditorServiceFakeAgentChatTerminalTab)?.coroutineScope?.coroutineContext?.get(Job)?.cancel()
  }
}

private class EditorServiceFakeAgentChatTerminalTab : AgentChatTerminalTab {
  override val component: JComponent = JPanel()
  override val preferredFocusableComponent: JComponent = JButton("focus")
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext = Job()
  }
  private val mutableSessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState
  override val keyEventsFlow: Flow<TerminalKeyEvent> = emptyFlow()

  val sentTexts: MutableList<EditorServiceSentTerminalText> = mutableListOf()

  fun setSessionState(state: TerminalViewSessionState) {
    mutableSessionState.value = state
  }

  override suspend fun captureOutputCheckpoint(): AgentChatTerminalOutputCheckpoint {
    return AgentChatTerminalOutputCheckpoint(regularEndOffset = 0, alternativeEndOffset = 0)
  }

  override suspend fun awaitOutputObservation(
    checkpoint: AgentChatTerminalOutputCheckpoint,
    timeoutMs: Long,
    idleMs: Long,
  ): AgentChatTerminalOutputObservation {
    return AgentChatTerminalOutputObservation(
      readiness = if (sessionState.value == TerminalViewSessionState.Terminated) {
        AgentChatTerminalInputReadiness.TERMINATED
      }
      else {
        AgentChatTerminalInputReadiness.READY
      },
      text = "",
    )
  }

  override fun sendText(text: String, shouldExecute: Boolean, useBracketedPasteMode: Boolean) {
    sentTexts += EditorServiceSentTerminalText(text, shouldExecute, useBracketedPasteMode)
  }

  override suspend fun awaitInitialMessageReadiness(
    timeoutMs: Long,
    idleMs: Long,
    checkpoint: AgentChatTerminalOutputCheckpoint?,
  ): AgentChatTerminalInputReadiness {
    return if (sessionState.value == TerminalViewSessionState.Terminated) {
      AgentChatTerminalInputReadiness.TERMINATED
    }
    else {
      AgentChatTerminalInputReadiness.READY
    }
  }

  override suspend fun readRecentOutputTail(): String = ""
}

private data class EditorServiceSentTerminalText(
  @JvmField val text: String,
  @JvmField val shouldExecute: Boolean,
  @JvmField val useBracketedPasteMode: Boolean = true,
)

private suspend fun publishThreadPresentation(
  file: AgentChatVirtualFile,
  path: String = file.projectPath,
  title: String,
  activity: AgentThreadActivity?,
): Int {
  val provider = checkNotNull(file.provider)
  return publishThreadPresentation(
    path = path,
    provider = provider,
    threadId = file.sessionId,
    title = title,
    activity = activity,
  )
}

private suspend fun publishThreadPresentation(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
  title: String,
  activity: AgentThreadActivity?,
): Int {
  val changeSet = service<AgentSessionThreadPresentationModel>().updateThread(
    path = path,
    provider = provider,
    threadId = threadId,
    title = title,
    activity = activity,
  )
  return AgentChatOpenTabPresentationInvalidator.invalidate(changeSet)
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
