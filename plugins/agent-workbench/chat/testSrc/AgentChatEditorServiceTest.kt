package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    val updatedTabs = runInUi {
      updateOpenAgentChatTabPresentation(
        titleByPathAndThreadIdentity = mapOf(
          (projectPath to "CODEX:thread-1") to "Renamed parent",
        ),
        activityByPathAndThreadIdentity = emptyMap(),
      )
    }
    assertThat(updatedTabs).isEqualTo(1)

    val files = openedChatFiles()
    assertThat(files.first { it.subAgentId == null }.threadTitle).isEqualTo("Renamed parent")
    assertThat(files.first { it.subAgentId == "sub-1" }.threadTitle).isEqualTo("Sub-agent label")
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
          AgentChatPendingCodexTabRebindRequest(
            pendingTabKey = file.tabKey,
            pendingThreadIdentity = file.threadIdentity,
            target = AgentChatPendingTabRebindTarget(
              threadIdentity = "CODEX:thread-3",
              threadId = "thread-3",
              shellCommand = listOf("codex", "resume", "thread-3"),
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
      .isEqualTo(AgentChatPendingCodexTabRebindStatus.REBOUND)

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
          AgentChatPendingCodexTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = "CODEX:new-2",
            target = AgentChatPendingTabRebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              shellCommand = listOf("codex", "resume", "thread-2"),
              threadTitle = "Recovered two",
              threadActivity = AgentThreadActivity.UNREAD,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.reboundBindings).isEqualTo(1)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingCodexTabRebindStatus.REBOUND)

    val filesByIdentity = openedChatFiles().associateBy { it.threadIdentity }
    assertThat(filesByIdentity).containsKey("CODEX:new-1")
    assertThat(filesByIdentity).containsKey("CODEX:thread-2")
    assertThat(filesByIdentity["CODEX:thread-2"]?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun testRebindOpenPendingCodexTabsFailsWhenConcreteIdentityIsAlreadyOpen(): Unit = timeoutRunBlocking {
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
          AgentChatPendingCodexTabRebindRequest(
            pendingTabKey = pendingTab.tabKey,
            pendingThreadIdentity = "CODEX:new-1",
            target = AgentChatPendingTabRebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              shellCommand = listOf("codex", "resume", "thread-2"),
              threadTitle = "Should not replace",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )
    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingCodexTabRebindStatus.TARGET_ALREADY_OPEN)
    assertThat(openedChatFiles().map { it.threadIdentity }).contains("CODEX:new-1", "CODEX:thread-2")
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
          AgentChatPendingCodexTabRebindRequest(
            pendingTabKey = "missing-tab-key",
            pendingThreadIdentity = "CODEX:new-1",
            target = AgentChatPendingTabRebindTarget(
              threadIdentity = "CODEX:thread-2",
              threadId = "thread-2",
              shellCommand = listOf("codex", "resume", "thread-2"),
              threadTitle = "Recovered",
              threadActivity = AgentThreadActivity.READY,
            ),
          )
        )
      )
    )

    assertThat(rebindReport.reboundBindings).isEqualTo(0)
    assertThat(rebindReport.outcomesByPath[projectPath].orEmpty().single().status)
      .isEqualTo(AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN)
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
  fun testCodexScopedRefreshSignalsEmitNormalizedPaths(): Unit = timeoutRunBlocking {
    val outputPath = "/work/project-terminal-output-delayed/"
    val signalWaiter = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        codexScopedRefreshSignals().first()
      }
    }

    notifyCodexTerminalOutputForRefresh(outputPath)

    assertThat(signalWaiter.await()).containsExactly("/work/project-terminal-output-delayed")
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
    val beforeCleanup = openedChatFiles()
    val alphaTabKey = beforeCleanup.first { it.subAgentId == "sub-alpha" }.tabKey
    val betaTabKey = beforeCleanup.first { it.subAgentId == "sub-beta" }.tabKey
    val baseTabKey = beforeCleanup.first { it.subAgentId == null }.tabKey

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
    initialMessageToken: String? = null,
  ) {
    val initialMessageDispatchPlan = AgentInitialMessageDispatchPlan(
      startupLaunchSpecOverride = startupShellCommandOverride?.let { command ->
        AgentSessionTerminalLaunchSpec(command = command, envVariables = startupShellEnvOverride.orEmpty())
      },
      initialComposedMessage = initialComposedMessage,
      initialMessageToken = initialMessageToken,
    )
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      shellCommand = shellCommand,
      shellEnvVariables = shellEnvVariables,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
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
      AgentChatEditorTabTitleProvider().getEditorTabTooltipHtml(project, file)?.toString()
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
