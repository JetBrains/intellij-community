// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.icons.AllIcons
import com.intellij.agent.workbench.ui.AgentWorkbenchActionIds
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.common.withAgentThreadActivityBadge
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentation
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionOutlineForkResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.ui.agentSessionThreadStatusIcon
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.IconManager
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatFileEditorProviderTest {
  @BeforeEach
  fun setUp() {
    clearAgentChatIconCacheForTests()
    IconLoader.activate()
    IconManager.activate(null)
  }

  @AfterEach
  fun tearDown() {
    clearAgentChatIconCacheForTests()
    service<AgentSessionThreadPresentationModel>().clearForTests()
    IconManager.deactivate()
    IconLoader.deactivate()
  }

  @Test
  fun keepsCodexResumeCommandAsTransientStartupOverride() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )

    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec.command).containsExactly("codex", "resume", "thread-1")
  }

  @Test
  fun startupLaunchSpecOverrideIsConsumedOnce() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      shellEnvVariables = mapOf("PATH" to "/usr/local/bin", "TERM" to "xterm-256color"),
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )
    file.setStartupLaunchSpecOverride(
      AgentSessionTerminalLaunchSpec(
        command = listOf("codex", "--", "-run this"),
        envVariables = mapOf("PATH" to "/custom/bin", "DISABLE_AUTOUPDATER" to "1"),
      )
    )

    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec.command).containsExactly("codex", "--", "-run this")
    assertThat(startupLaunchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/custom/bin", "DISABLE_AUTOUPDATER" to "1"))

    assertThat(file.consumeStartupLaunchSpecOverride()).isNull()
  }

  @Test
  fun startupLaunchSpecOverrideDoesNotPersistRemoteResumeCommand() {
    val remoteCommand = listOf(
      "codex",
      "-c",
      "check_for_update_on_startup=false",
      "--remote",
      "ws://127.0.0.1:31337",
      "resume",
      "thread-1",
    )
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = remoteCommand,
      shellEnvVariables = mapOf("PATH" to "/usr/local/bin", "TERM" to "xterm-256color"),
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )
    file.setStartupLaunchSpecOverride(
      AgentSessionTerminalLaunchSpec(
        command = listOf("codex", "--", "-run this"),
        envVariables = mapOf("PATH" to "/custom/bin", "DISABLE_AUTOUPDATER" to "1"),
      )
    )
    file.updateLaunchMode(AgentSessionLaunchMode.YOLO.name)

    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec.command).containsExactly("codex", "--", "-run this")

    assertThat(file.consumeStartupLaunchSpecOverride()).isNull()

    val snapshot = file.toSnapshot()

    val store = AgentChatTabsStateService(null)
    store.upsert(snapshot)
    try {
      val loaded = store.load(snapshot.tabKey)
      assertThat(loaded?.identity).isEqualTo(snapshot.identity)
      assertThat(loaded?.runtime?.threadId).isEqualTo(snapshot.runtime.threadId)
      assertThat(loaded?.runtime?.launchMode).isEqualTo("yolo")
    }
    finally {
      store.delete(snapshot.tabKey)
    }
  }

  @Test
  fun fileEditorStateRoundTripsMetadataWithoutCommand() {
    val dispatchSteps = listOf(
      AgentInitialMessageDispatchStep(text = "first step"),
      AgentInitialMessageDispatchStep(text = "second step"),
    )
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-state",
      threadId = "thread-state",
      threadTitle = "State thread",
      subAgentId = "alpha",
      threadActivity = AgentThreadActivity.UNREAD,
      pendingCreatedAtMs = 100,
      pendingFirstInputAtMs = 200,
      pendingLaunchMode = AgentSessionLaunchMode.STANDARD.name,
      launchMode = AgentSessionLaunchMode.YOLO.name,
      launchProfileId = "profile:codex-yolo",
      newThreadRebindRequestedAtMs = 300,
      initialMessageDispatchSteps = dispatchSteps,
      initialMessageDispatchStepIndex = 1,
      initialMessageToken = "token-state",
      initialMessageSent = false,
    )
    val element = Element("state")

    val startupIntent = AgentChatStartupIntent.NewSession(
      provider = AgentSessionProvider.from("codex"),
      launchMode = AgentSessionLaunchMode.YOLO,
      launchProfileId = "profile:codex-yolo",
    )

    writeAgentChatFileEditorState(AgentChatFileEditorState(snapshot = snapshot, startupIntent = startupIntent), element)

    assertThat(element.getAttributeValue("shellCommand")).isNull()
    assertThat(element.getAttributeValue("shellEnvVariables")).isNull()
    assertThat(element.getAttributeValue("startupKind")).isEqualTo("newSession")
    assertThat(element.getAttributeValue("startupProvider")).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(element.getAttributeValue("startupLaunchMode")).isEqualTo(AgentSessionLaunchMode.YOLO.name)
    assertThat(element.getAttributeValue("startupLaunchProfileId")).isEqualTo("profile:codex-yolo")
    assertThat(element.getAttributeValue("launchMode")).isEqualTo("yolo")
    assertThat(element.getAttributeValue("launchProfileId")).isEqualTo("profile:codex-yolo")
    assertThat(element.getAttributeValue("initialPromptMessage")).isNull()
    assertThat(element.getAttributeValue("initialPromptMode")).isNull()
    assertThat(element.getAttributeValue("initialPromptToken")).isNull()
    assertThat(element.getAttributeValue("initialPromptDeliveryStatus")).isNull()
    assertThat(element.getAttributeValue("initialPromptDeliveryChannel")).isNull()
    assertThat(element.getAttributeValue("terminalPromptDispatchStepIndex")).isNull()
    assertThat(element.getChild("initialMessageDispatchSteps")).isNull()

    val file = AgentChatVirtualFile(
      projectPath = snapshot.identity.projectPath,
      threadIdentity = snapshot.identity.threadIdentity,
      shellCommand = emptyList(),
      threadId = snapshot.runtime.threadId,
      threadTitle = snapshot.runtime.threadTitle,
      subAgentId = snapshot.identity.subAgentId,
      projectHash = snapshot.identity.projectHash,
    )
    val restoredState = readAgentChatFileEditorState(element, file)
    val restored = restoredState.snapshot

    assertThat(restored?.identity).isEqualTo(snapshot.identity)
    assertThat(restored?.runtime?.threadId).isEqualTo("thread-state")
    assertThat(restored?.runtime?.threadTitle).isEqualTo("State thread")
    assertThat(restored?.runtime?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(restored?.runtime?.pendingCreatedAtMs).isEqualTo(100)
    assertThat(restored?.runtime?.pendingFirstInputAtMs).isEqualTo(200)
    assertThat(restored?.runtime?.pendingLaunchMode).isEqualTo(AgentSessionLaunchMode.STANDARD.name)
    assertThat(restored?.runtime?.launchMode).isEqualTo("yolo")
    assertThat(restored?.runtime?.launchProfileId).isEqualTo("profile:codex-yolo")
    assertThat(restored?.runtime?.newThreadRebindRequestedAtMs).isEqualTo(300)
    assertThat(restored?.runtime?.initialMessageDispatchSteps).isEmpty()
    assertThat(restored?.runtime?.initialMessageDispatchStepIndex).isEqualTo(0)
    assertThat(restored?.runtime?.initialMessageToken).isNull()
    assertThat(restored?.runtime?.initialMessageSent).isFalse()
    assertThat(restored?.runtime?.initialPromptRecord).isNull()
    assertThat(restored?.runtime?.terminalPromptDispatch).isNull()
    assertThat(restoredState.startupIntent).isEqualTo(startupIntent)
    assertThat(file.launchMode).isEqualTo("yolo")
    assertThat(file.launchProfileId).isEqualTo("profile:codex-yolo")
  }

  @Test
  fun fileEditorStateReadsLegacyStartupProviderWhenThreadIdentityHasNoProvider() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "new-legacy-thread",
      shellCommand = emptyList(),
      threadId = "new-legacy-thread",
      threadTitle = "Legacy new thread",
      subAgentId = null,
      projectHash = "hash-1",
    )
    val element = Element("state").apply {
      setAttribute("version", "4")
      setAttribute("projectHash", "hash-1")
      setAttribute("projectPath", "/work/project-a")
      setAttribute("threadIdentity", "new-legacy-thread")
      setAttribute("threadId", "new-legacy-thread")
      setAttribute("threadTitle", "Legacy new thread")
      setAttribute("threadActivity", AgentThreadActivity.READY.name)
      setAttribute("pendingLaunchMode", AgentSessionLaunchMode.STANDARD.name)
      setAttribute("startupKind", "newSession")
      setAttribute("startupProvider", AgentSessionProvider.from("codex").value)
      setAttribute("startupLaunchMode", AgentSessionLaunchMode.YOLO.name)
    }

    val restoredState = readAgentChatFileEditorState(element, file)

    assertThat(restoredState.startupIntent).isEqualTo(
      AgentChatStartupIntent.NewSession(
        provider = AgentSessionProvider.from("codex"),
        launchMode = AgentSessionLaunchMode.YOLO,
      )
    )
  }

  @Test
  fun fileEditorStateKeepsStartupProviderFallbackWhenLaunchProfileIsPersisted() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "new-profile-thread",
      shellCommand = emptyList(),
      threadId = "new-profile-thread",
      threadTitle = "Profile thread",
      subAgentId = null,
      projectHash = "hash-1",
    )
    val element = Element("state").apply {
      setAttribute("version", "5")
      setAttribute("projectHash", "hash-1")
      setAttribute("projectPath", "/work/project-a")
      setAttribute("threadIdentity", "new-profile-thread")
      setAttribute("threadId", "new-profile-thread")
      setAttribute("threadTitle", "Profile thread")
      setAttribute("threadActivity", AgentThreadActivity.READY.name)
      setAttribute("pendingLaunchMode", AgentSessionLaunchMode.STANDARD.name)
      setAttribute("startupKind", "newSession")
      setAttribute("startupProvider", AgentSessionProvider.from("codex").value)
      setAttribute("startupLaunchMode", AgentSessionLaunchMode.YOLO.name)
      setAttribute("startupLaunchProfileId", "profile:missing")
    }

    val restoredState = readAgentChatFileEditorState(element, file)

    assertThat(restoredState.startupIntent).isEqualTo(
      AgentChatStartupIntent.NewSession(
        provider = AgentSessionProvider.from("codex"),
        launchMode = AgentSessionLaunchMode.YOLO,
        launchProfileId = "profile:missing",
      )
    )
  }

  @Test
  fun fileEditorStateIgnoresLegacyPromptDispatchMetadata() {
    val element = Element("state").apply {
      setAttribute("version", "3")
      setAttribute("projectHash", "hash-1")
      setAttribute("projectPath", "/work/project-a")
      setAttribute("threadIdentity", "CODEX:thread-legacy")
      setAttribute("threadId", "thread-legacy")
      setAttribute("threadTitle", "Legacy thread")
      setAttribute("threadActivity", AgentThreadActivity.UNREAD.name)
      setAttribute("initialMessageDispatchStepIndex", "1")
      setAttribute("initialMessageToken", "legacy-token")
      setAttribute("initialMessageSent", "false")
      addContent(Element("initialMessageDispatchSteps").apply {
        addContent(Element("step").apply { text = "first step" })
        addContent(Element("step").apply { text = "second step" })
      })
    }
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-legacy",
      shellCommand = emptyList(),
      threadId = "thread-legacy",
      threadTitle = "Legacy thread",
      subAgentId = null,
      projectHash = "hash-1",
    )

    val restored = readAgentChatFileEditorState(element, file).snapshot

    assertThat(restored?.runtime?.threadId).isEqualTo("thread-legacy")
    assertThat(restored?.runtime?.threadTitle).isEqualTo("Legacy thread")
    assertThat(restored?.runtime?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(restored?.runtime?.initialMessageDispatchSteps).isEmpty()
    assertThat(restored?.runtime?.initialMessageDispatchStepIndex).isEqualTo(0)
    assertThat(restored?.runtime?.initialMessageToken).isNull()
    assertThat(restored?.runtime?.initialMessageSent).isFalse()
    assertThat(restored?.runtime?.initialPromptRecord).isNull()
    assertThat(restored?.runtime?.terminalPromptDispatch).isNull()
  }

  @Test
  fun fileEditorStateHydratesRestoredPresentationBeforeSetState() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-restored-title",
      threadId = "thread-restored-title",
      threadTitle = "Restored thread",
      subAgentId = null,
      threadActivity = AgentThreadActivity.UNREAD,
    )
    val element = Element("state")
    writeAgentChatFileEditorState(AgentChatFileEditorState(snapshot = snapshot), element)
    val file = AgentChatVirtualFile(
      fileSystem = createStandaloneAgentChatVirtualFileSystemForTest(),
      resolution = AgentChatTabResolution.Unresolved(snapshot.tabKey),
    )
    val titleProvider = AgentChatEditorTabTitleProvider()
    val project = ProjectManager.getInstance().defaultProject
    val fallbackTitle = AgentChatBundle.message("chat.filetype.name")

    assertThat(file.projectPath).isBlank()
    assertThat(file.threadIdentity).isBlank()
    assertThat(titleProvider.getEditorTabTitle(project, file)).isEqualTo(fallbackTitle)

    val restoredState = readAgentChatFileEditorState(element, file)

    assertThat(restoredState.snapshot).isEqualTo(snapshot)
    assertThat(file.projectPath).isEqualTo(snapshot.identity.projectPath)
    assertThat(file.threadIdentity).isEqualTo(snapshot.identity.threadIdentity)
    assertThat(file.threadTitle).isEqualTo("Restored thread")
    assertThat(file.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(titleProvider.getEditorTabTitle(project, file)).isEqualTo("Restored thread")
  }

  @Test
  fun fileEditorStateDoesNotHydrateVirtualFileWhenStateKeyDoesNotMatch() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-state-title",
      threadId = "thread-state-title",
      threadTitle = "State title",
      subAgentId = null,
    )
    val otherSnapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-other-title",
      threadId = "thread-other-title",
      threadTitle = "Other title",
      subAgentId = null,
    )
    val element = Element("state")
    writeAgentChatFileEditorState(AgentChatFileEditorState(snapshot = snapshot), element)
    val file = AgentChatVirtualFile(
      fileSystem = createStandaloneAgentChatVirtualFileSystemForTest(),
      resolution = AgentChatTabResolution.Unresolved(otherSnapshot.tabKey),
    )

    val restoredState = readAgentChatFileEditorState(element, file)

    assertThat(restoredState.snapshot).isNull()
    assertThat(file.projectPath).isBlank()
    assertThat(file.threadIdentity).isBlank()
    assertThat(file.threadTitle).isEqualTo(AgentChatBundle.message("chat.filetype.name"))
  }

  @Test
  fun registersAgentChatDescriptorExtensions() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.chat.xml")) {
      "Module descriptor intellij.agent.workbench.chat.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<fileIconProvider implementation=\"com.intellij.agent.workbench.chat.AgentChatFileIconProvider\"/>")
      .contains(
        "<applicationService serviceInterface=\"com.intellij.platform.ai.agent.sessions.core.providers.AgentOpenTopLevelThreadDispatchService\"",
      )
      .contains(
        "serviceImplementation=\"com.intellij.agent.workbench.chat.AgentChatOpenTopLevelThreadDispatchService\"/>",
      )
      .contains("AgentWorkbenchSessions.ThreadOutline.Popup")
      .contains(AgentWorkbenchActionIds.Sessions.ThreadOutline.START_NEW_CONVERSATION_FROM_HERE)
      .doesNotContain("StructureViewPopupMenu")
  }

  @Test
  fun keepsClaudeResumeCommandAsTransientStartupOverride() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CLAUDE:session-1",
      shellCommand = listOf("claude", "--resume", "session-1"),
      threadId = "session-1",
      threadTitle = "Session One",
      subAgentId = null,
    )

    val startupLaunchSpec = checkNotNull(file.consumeStartupLaunchSpecOverride())
    assertThat(startupLaunchSpec.command).containsExactly("claude", "--resume", "session-1")
  }

  @Test
  fun usesAgentChatProtocolAndRoundTripsDescriptor() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
      projectHash = "hash-1",
    )

    assertThat(file.fileSystem.protocol).isEqualTo(AGENT_CHAT_PROTOCOL)
    val tabKey = AgentChatTabKey.parsePath(file.path)
    assertThat(tabKey).isNotNull
    assertThat(tabKey?.value).isEqualTo(file.tabKey)
    assertThat(file.path).startsWith("$AGENT_CHAT_URL_SCHEMA_VERSION/")
  }

  @Test
  fun forbidsTabSplitForChatFiles() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
    )

    assertThat(file.getUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT)).isTrue()
  }

  @Test
  fun doesNotExposePlatformStructureViewForChatFiles() {
    assertThat(AgentChatFileEditorProvider()).isNotInstanceOf(StructureViewFileEditorProvider::class.java)
  }

  @Test
  fun threadOutlinePanelLoadsOutlineAndUpdatesModel() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val file = AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-42",
        shellCommand = emptyList(),
        threadId = "thread-42",
        threadTitle = "Resolve the current merge conflicts",
        subAgentId = null,
      )
      val outlineLoadGate = CompletableDeferred<Unit>()
      val outline = AgentSessionThreadOutline(
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-42",
        title = "Resolve the current merge conflicts",
        updatedAt = 1L,
        items = listOf(
          AgentSessionOutlineItem(
            id = "root-work",
            kind = AgentSessionOutlineItemKind.AGENT_WORK,
            title = "Resolve the current merge conflicts",
            children = listOf(
              AgentSessionOutlineItem(
                id = "user-1",
                kind = AgentSessionOutlineItemKind.USER_PROMPT,
                title = "",
                preview = "Resolve the current merge conflicts",
                timestampMs = 2_000L,
              ),
              AgentSessionOutlineItem(
                id = "work-1",
                kind = AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
                title = "I'll inspect the Git operation state",
                timestampMs = 1_000L,
                children = listOf(
                  AgentSessionOutlineItem(
                    id = "tool-1",
                    kind = AgentSessionOutlineItemKind.TOOL_CALL,
                    title = "git status",
                    preview = "Updated at 10.06.26, 10:58",
                  ),
                ),
              ),
            ),
          ),
        ),
      )
      var loadCalls = 0
      val bridge = ChatTestProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        icon = EmptyIcon.create(18, 18),
        outlineLoader = { _, _, _ ->
          loadCalls++
          outlineLoadGate.await()
          outline
        },
      )

      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        val panel = createThreadOutlinePanel(project)
        try {
          panel.selectFileForTestsOnEdt(file)
          assertThat(panel.modelForTestsOnEdt().singleNode().location).isEqualTo(AgentChatBundle.message("chat.thread.outline.loading"))
          waitForCondition { loadCalls == 1 }

          outlineLoadGate.complete(Unit)
          val providerRootId = AgentChatThreadOutlineId.Item("0")
          waitForCondition {
            panel.modelForTestsOnEdt().entriesById[providerRootId]?.childIds ==
              listOf(AgentChatThreadOutlineId.Item("0/0"), AgentChatThreadOutlineId.Item("0/1"))
          }

          val model = panel.modelForTestsOnEdt()
          assertThat(model.autoExpandIds).containsExactly(providerRootId)
          assertThat(model.entriesById.getValue(providerRootId).childIds)
            .containsExactly(AgentChatThreadOutlineId.Item("0/0"), AgentChatThreadOutlineId.Item("0/1"))
          val promptNode = model.entriesById.getValue(AgentChatThreadOutlineId.Item("0/0")).node
          assertThat(promptNode.title).isEqualTo("user: Resolve the current merge conflicts")
          assertThat(promptNode.icon).isSameAs(AllIcons.General.User)
          assertThat(promptNode.timestamp).isEqualTo(DateFormatUtil.formatPrettyDateTime(2_000L))
          assertThat(promptNode.location).isNull()
          assertThat(promptNode.tooltip).isEqualTo(
            "Resolve the current merge conflicts\n" +
            AgentChatBundle.message("chat.thread.outline.timestamp", DateFormatUtil.formatPrettyDateTime(2_000L))
          )
          val assistantNode = model.entriesById.getValue(AgentChatThreadOutlineId.Item("0/1")).node
          assertThat(assistantNode.title).isEqualTo("assistant: I'll inspect the Git operation state")
          assertThat(assistantNode.timestamp).isEqualTo(DateFormatUtil.formatPrettyDateTime(1_000L))
          assertThat(assistantNode.location).isNull()
          assertThat(assistantNode.tooltip)
            .isEqualTo(AgentChatBundle.message("chat.thread.outline.timestamp", DateFormatUtil.formatPrettyDateTime(1_000L)))
          assertThat(model.entriesById.getValue(AgentChatThreadOutlineId.Item("0/1")).childIds)
            .containsExactly(AgentChatThreadOutlineId.Item("0/1/0"))
        }
        finally {
          disposePanel(panel)
        }
      }
    }
  }

  @Test
  fun threadOutlineRefreshesLoadedOutlineOnActiveThreadUpdate() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val file = AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-refresh",
        shellCommand = emptyList(),
        threadId = "thread-refresh",
        threadTitle = "Refresh thread",
        subAgentId = null,
      )
      val updateEvents = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 1)
      var outline = testOutline(
        updatedAt = 1L,
        items = listOf(testOutlineItem(id = "prompt-1", title = "Initial prompt")),
      )
      val loadCalls = AtomicInteger()
      val bridge = ChatTestProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        icon = EmptyIcon.create(18, 18),
        outlineLoader = { path, threadId, subAgentId ->
          assertThat(path).isEqualTo("/work/project-a")
          assertThat(threadId).isEqualTo("thread-refresh")
          assertThat(subAgentId).isNull()
          loadCalls.incrementAndGet()
          outline
        },
        activeThreadUpdateEventsProvider = { path, threadId ->
          assertThat(path).isEqualTo("/work/project-a")
          assertThat(threadId).isEqualTo("thread-refresh")
          updateEvents
        },
      )

      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        val panel = createThreadOutlinePanel(project)
        try {
          panel.selectFileForTestsOnEdt(file)
          waitForCondition { panel.modelForTestsOnEdt().rootNodeTitles() == listOf("user: Initial prompt") }
          assertThat(loadCalls.get()).isEqualTo(1)
          waitForCondition { updateEvents.subscriptionCount.value > 0 }

          outline = testOutline(
            updatedAt = 2L,
            items = listOf(
              testOutlineItem(id = "prompt-1", title = "Initial prompt"),
              testOutlineItem(id = "assistant-1", kind = AgentSessionOutlineItemKind.ASSISTANT_RESPONSE, title = "Assistant reply"),
            ),
          )
          assertThat(updateEvents.tryEmit(testUpdateEvent())).isTrue()

          waitForCondition { panel.modelForTestsOnEdt().rootNodeTitles() == listOf("user: Initial prompt", "assistant: Assistant reply") }
          assertThat(loadCalls.get()).isEqualTo(2)
        }
        finally {
          disposePanel(panel)
        }
      }
    }
  }

  @Test
  fun threadOutlineLoadsAfterSelectedPendingThreadRebindsOnScopedRefresh() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val file = AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:new-thread-rebind",
        shellCommand = emptyList(),
        threadId = "new-thread-rebind",
        threadTitle = "New thread",
        subAgentId = null,
      )
      val outline = AgentSessionThreadOutline(
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-rebound",
        title = "Rebound thread",
        updatedAt = 1L,
        items = listOf(testOutlineItem(id = "prompt-rebound", title = "Prompt after rebind")),
      )
      val loadCalls = AtomicInteger()
      val bridge = ChatTestProviderBridge(
        provider = AgentSessionProvider.from("codex"),
        icon = EmptyIcon.create(18, 18),
        outlineLoader = { path, threadId, subAgentId ->
          assertThat(path).isEqualTo("/work/project-a")
          assertThat(threadId).isEqualTo("thread-rebound")
          assertThat(subAgentId).isNull()
          loadCalls.incrementAndGet()
          outline
        },
      )

      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        val panel = createThreadOutlinePanel(project)
        try {
          panel.selectFileForTestsOnEdt(file)
          assertThat(panel.modelForTestsOnEdt().singleNode().location)
            .isEqualTo(AgentChatBundle.message("chat.thread.outline.unavailable"))
          assertThat(loadCalls.get()).isZero()

          assertThat(
            file.rebindPendingThread(
              threadIdentity = "CODEX:thread-rebound",
              threadId = "thread-rebound",
              threadTitle = "Rebound thread",
              threadActivity = AgentThreadActivity.READY,
            )
          ).isTrue()

          waitForCondition {
            notifyAgentChatScopedRefresh(
              provider = AgentSessionProvider.from("codex"),
              projectPath = "/work/project-a",
              threadId = "thread-rebound",
            )
            panel.modelForTestsOnEdt().rootNodeTitles() == listOf("user: Prompt after rebind")
          }
          assertThat(loadCalls.get()).isGreaterThanOrEqualTo(1)
        }
        finally {
          disposePanel(panel)
        }
      }
    }
  }

  @Test
  fun threadOutlineShowsSingleTopLevelStatusRowsForFallbackOutlines() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val cases: List<Pair<AgentSessionThreadOutline?, String>> = listOf(
        null to AgentChatBundle.message("chat.thread.outline.unavailable"),
        AgentSessionThreadOutline(
          provider = AgentSessionProvider.from("codex"),
          threadId = "thread-empty",
          title = "Empty thread",
          updatedAt = 1L,
          items = emptyList(),
        ) to AgentChatBundle.message("chat.thread.outline.empty"),
      )

      cases.forEachIndexed { index, (outline, expectedStatus) ->
        val file = AgentChatVirtualFile(
          projectPath = "/work/project-a",
          threadIdentity = "CODEX:thread-status-$index",
          shellCommand = emptyList(),
          threadId = "thread-status-$index",
          threadTitle = "Status thread $index",
          subAgentId = null,
        )
        val bridge = ChatTestProviderBridge(
          provider = AgentSessionProvider.from("codex"),
          icon = EmptyIcon.create(18, 18),
          outlineLoader = { _, _, _ -> outline },
        )

        AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
          val panel = createThreadOutlinePanel(project)
          try {
            panel.selectFileForTestsOnEdt(file)
            waitForCondition { panel.modelForTestsOnEdt().singleNode().location == expectedStatus }
            val model = panel.modelForTestsOnEdt()
            assertThat(model.rootIds).hasSize(1)
            assertThat(model.entriesById.getValue(model.rootIds.single()).childIds).isEmpty()
          }
          finally {
            disposePanel(panel)
          }
        }
      }
    }
  }

  @Test
  fun threadOutlineRowsDelegateLiveNavigationToProvider() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val file = AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "PI:thread-nav",
        shellCommand = emptyList(),
        threadId = "thread-nav",
        threadTitle = "Navigation thread",
        subAgentId = null,
      )
      val outlineItem = AgentSessionOutlineItem(
        id = "entry-nav",
        kind = AgentSessionOutlineItemKind.USER_PROMPT,
        title = "Open entry",
        preview = "Jump to this entry",
      )
      val outline = AgentSessionThreadOutline(
        provider = AgentSessionProvider.from("pi"),
        threadId = "thread-nav",
        title = "Navigation thread",
        updatedAt = 1L,
        items = listOf(outlineItem),
      )
      val navigationCalls = LinkedBlockingQueue<OutlineNavigationCall>()
      val bridge = ChatTestProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        icon = EmptyIcon.create(18, 18),
        outlineLoader = { _, _, _ -> outline },
        canNavigateOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
          path == "/work/project-a" &&
          threadId == "thread-nav" &&
          itemId == "entry-nav" &&
          subAgentId == null &&
          tabKey == file.tabKey
        },
        navigateOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
          navigationCalls.add(OutlineNavigationCall(path, threadId, itemId, subAgentId, tabKey))
          true
        },
      )

      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        val panel = createThreadOutlinePanel(project)
        try {
          panel.selectFileForTestsOnEdt(file)
          waitForCondition { panel.modelForTestsOnEdt().rootNodeTitles() == listOf("user: Open entry") }

          assertThat(panel.navigateOutlineIdForTestsOnEdt(AgentChatThreadOutlineId.Item("0"))).isTrue()

          assertThat(navigationCalls.poll(5, TimeUnit.SECONDS))
            .isEqualTo(OutlineNavigationCall("/work/project-a", "thread-nav", "entry-nav", null, file.tabKey))
        }
        finally {
          disposePanel(panel)
        }
      }
    }
  }

  @Test
  fun threadOutlinePopupHasKeyboardActivation() {
    val project = ProjectManager.getInstance().defaultProject
    val panel = createThreadOutlinePanel(project)
    try {
      assertThat(panel.hasPopupKeyboardActivationForTestsOnEdt()).isTrue()
    }
    finally {
      disposePanel(panel)
    }
  }

  @Test
  fun threadOutlineForkActionStaysHiddenWhenLiveForkUnavailable() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "PI:thread-fork",
      shellCommand = emptyList(),
      threadId = "thread-fork",
      threadTitle = "Fork thread",
      subAgentId = null,
    )
    val item = AgentSessionOutlineItem(
      id = "entry-fork",
      kind = AgentSessionOutlineItemKind.USER_PROMPT,
      title = "Fork from here",
    )
    val bridge = ChatTestProviderBridge(
      provider = AgentSessionProvider.from("pi"),
      icon = EmptyIcon.create(18, 18),
      canShowForkOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
        path == "/work/project-a" &&
        threadId == "thread-fork" &&
        itemId == "entry-fork" &&
        subAgentId == null &&
        tabKey == file.tabKey
      },
    )
    val action = AgentChatThreadOutlineForkAction()
    val event = threadOutlineForkActionEvent(action, threadOutlineForkTarget(file, bridge.sessionSource, item))

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun threadOutlineForkActionEnablesWhenLiveForkAvailableFromCustomDataKey() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "PI:thread-fork",
      shellCommand = emptyList(),
      threadId = "thread-fork",
      threadTitle = "Fork thread",
      subAgentId = null,
    )
    val item = AgentSessionOutlineItem(
      id = "entry-fork",
      kind = AgentSessionOutlineItemKind.USER_PROMPT,
      title = "Fork from here",
    )
    val bridge = ChatTestProviderBridge(
      provider = AgentSessionProvider.from("pi"),
      icon = EmptyIcon.create(18, 18),
      canShowForkOutlineItem = { _, _, _, _, _ -> true },
      canForkOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
        path == "/work/project-a" &&
        threadId == "thread-fork" &&
        itemId == "entry-fork" &&
        subAgentId == null &&
        tabKey == file.tabKey
      },
    )
    val action = AgentChatThreadOutlineForkAction()
    val event = threadOutlineForkActionEvent(action, threadOutlineForkTarget(file, bridge.sessionSource, item))

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun threadOutlinePopupBecomesAvailableWhenActiveThreadReportsLiveForkSupport() {
    timeoutRunBlocking {
      val project = ProjectManager.getInstance().defaultProject
      val file = AgentChatVirtualFile(
        projectPath = "/work/project-a",
        threadIdentity = "PI:thread-fork-refresh",
        shellCommand = emptyList(),
        threadId = "thread-fork-refresh",
        threadTitle = "Fork refresh thread",
        subAgentId = null,
      )
      val updateEvents = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 1)
      var liveForkAvailable = false
      val bridge = ChatTestProviderBridge(
        provider = AgentSessionProvider.from("pi"),
        icon = EmptyIcon.create(18, 18),
        outlineLoader = { _, _, _ ->
          testOutline(
            updatedAt = 1L,
            items = listOf(testOutlineItem(id = "entry-fork-refresh", title = "Fork from here")),
          )
        },
        activeThreadUpdateEventsProvider = { path, threadId ->
          assertThat(path).isEqualTo("/work/project-a")
          assertThat(threadId).isEqualTo("thread-fork-refresh")
          updateEvents
        },
        canShowForkOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
          path == "/work/project-a" &&
          threadId == "thread-fork-refresh" &&
          itemId == "entry-fork-refresh" &&
          subAgentId == null &&
          tabKey == file.tabKey
        },
        canForkOutlineItem = { path, threadId, itemId, subAgentId, tabKey ->
          liveForkAvailable &&
          path == "/work/project-a" &&
          threadId == "thread-fork-refresh" &&
          itemId == "entry-fork-refresh" &&
          subAgentId == null &&
          tabKey == file.tabKey
        },
      )

      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        val panel = createThreadOutlinePanel(project)
        try {
          panel.selectFileForTestsOnEdt(file)
          waitForCondition { panel.modelForTestsOnEdt().rootNodeTitles() == listOf("user: Fork from here") }
          waitForCondition { updateEvents.subscriptionCount.value > 0 }
          val outlineId = AgentChatThreadOutlineId.Item("0")
          assertThat(panel.canShowPopupForOutlineIdForTestsOnEdt(outlineId)).isFalse()

          liveForkAvailable = true
          assertThat(updateEvents.tryEmit(testUpdateEvent(path = "/work/project-a", threadId = "thread-fork-refresh"))).isTrue()

          waitForCondition { panel.canShowPopupForOutlineIdForTestsOnEdt(outlineId) }
        }
        finally {
          disposePanel(panel)
        }
      }
    }
  }

  @Test
  fun threadOutlineForkActionStaysHiddenForProvidersWithoutStaticSupport() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-fork",
      shellCommand = emptyList(),
      threadId = "thread-fork",
      threadTitle = "Fork thread",
      subAgentId = null,
    )
    val item = AgentSessionOutlineItem(
      id = "entry-fork",
      kind = AgentSessionOutlineItemKind.USER_PROMPT,
      title = "Fork from here",
    )
    val bridge = ChatTestProviderBridge(
      provider = AgentSessionProvider.from("codex"),
      icon = EmptyIcon.create(18, 18),
      canForkOutlineItem = { _, _, _, _, _ -> true },
    )
    val action = AgentChatThreadOutlineForkAction()
    val event = threadOutlineForkActionEvent(action, threadOutlineForkTarget(file, bridge.sessionSource, item))

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun usesLowercaseBase36TabKey() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
    )

    val uppercasePath = "$AGENT_CHAT_URL_SCHEMA_VERSION/${snapshot.tabKey.value.uppercase()}"
    val truncatedPath = "$AGENT_CHAT_URL_SCHEMA_VERSION/${snapshot.tabKey.value.dropLast(1)}"

    assertThat(snapshot.tabKey.value).matches("[0-9a-z]{50}")
    assertThat(AgentChatTabKey.parsePath("$AGENT_CHAT_URL_SCHEMA_VERSION/${snapshot.tabKey.value}")?.value).isEqualTo(snapshot.tabKey.value)
    assertThat(AgentChatTabKey.parsePath(uppercasePath)).isNull()
    assertThat(AgentChatTabKey.parsePath(truncatedPath)).isNull()
  }

  @Test
  fun updatesDescriptorWhenTabTitleChanges() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-7",
      shellCommand = listOf("codex", "resume", "thread-7"),
      threadId = "thread-7",
      threadTitle = "Initial title",
      subAgentId = null,
      projectHash = "hash-1",
    )

    val originalPath = file.path
    file.updateBootstrapThreadTitle("Renamed title")
    assertThat(file.path).isEqualTo(originalPath)
  }

  @Test
  fun sharedThreadPresentationFullRefreshReplacesScopedEntries() {
    val model = service<AgentSessionThreadPresentationModel>()
    val refreshedKey = presentationKey("/work/project-a", AgentSessionProvider.from("codex"), "thread-1")
    val removedKey = presentationKey("/work/project-a", AgentSessionProvider.from("codex"), "thread-2")
    val otherProviderKey = presentationKey("/work/project-a", AgentSessionProvider.from("claude"), "session-1")
    val otherPathKey = presentationKey("/work/project-b", AgentSessionProvider.from("codex"), "thread-3")
    model.replaceForTests(
      mapOf(
        refreshedKey to AgentSessionThreadPresentation(title = "Old title", activity = AgentThreadActivity.READY),
        removedKey to AgentSessionThreadPresentation(title = "Removed title", activity = AgentThreadActivity.UNREAD),
        otherProviderKey to AgentSessionThreadPresentation(title = "Claude title", activity = AgentThreadActivity.PROCESSING),
        otherPathKey to AgentSessionThreadPresentation(title = "Other path", activity = AgentThreadActivity.READY),
      )
    )

    val changeSet = model.updateProviderSnapshot(
      provider = AgentSessionProvider.from("codex"),
      authoritativePaths = setOf("/work/project-a/"),
      threadsByPath = mapOf(
        "/work/project-a" to listOf(
          threadModel(AgentSessionProvider.from("codex"), "thread-1", "Renamed title", AgentThreadActivity.PROCESSING)
        ),
      ),
    )

    assertThat(changeSet.changedKeys).containsExactly(refreshedKey)
    assertThat(changeSet.removedKeys).containsExactly(removedKey)
    assertThat(model.resolve(refreshedKey))
      .isEqualTo(
        AgentSessionThreadPresentation(
          title = "Renamed title",
          activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING),
          updatedAt = 1L,
        )
      )
    assertThat(model.resolve(removedKey)).isNull()
    assertThat(model.resolve(otherProviderKey))
      .isEqualTo(AgentSessionThreadPresentation(title = "Claude title", activity = AgentThreadActivity.PROCESSING))
    assertThat(model.resolve(otherPathKey))
      .isEqualTo(AgentSessionThreadPresentation(title = "Other path", activity = AgentThreadActivity.READY))
  }

  @Test
  fun sharedThreadPresentationActivityOnlyUpdateKeepsExistingTitle() {
    timeoutRunBlocking {
      val model = service<AgentSessionThreadPresentationModel>()
      val key = presentationKey("/work/project-a", AgentSessionProvider.from("codex"), "thread-1")
      model.updateThread(
        path = "/work/project-a",
        provider = AgentSessionProvider.from("codex"),
        threadId = "thread-1",
        title = "Existing title",
        activity = AgentThreadActivity.READY,
      )

      val changeSet = model.updateActivityHints(
        provider = AgentSessionProvider.from("codex"),
        updates = listOf(
          com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadActivityPresentationUpdate(
            path = "/work/project-a",
            threadId = "thread-1",
            activity = AgentThreadActivity.UNREAD,
          )
        ),
      )

      assertThat(changeSet.changedKeys).containsExactly(key)
      assertThat(model.resolve(key))
        .isEqualTo(AgentSessionThreadPresentation(title = "Existing title", activity = AgentThreadActivity.UNREAD))
    }
  }

  @Test
  fun restoreMaterializationKeepsPersistedPresentationAsBootstrapFallback() {
    timeoutRunBlocking {
      val snapshot = AgentChatTabSnapshot.create(
        projectHash = "hash-1",
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-restore",
        threadId = "thread-restore",
        threadTitle = "Restored thread",
        subAgentId = null,
        threadActivity = AgentThreadActivity.UNREAD,
      )
      val tabsService = service<AgentChatTabsService>()
      val model = service<AgentSessionThreadPresentationModel>()
      val key = presentationKey(snapshot.identity.projectPath, AgentSessionProvider.from("codex"), snapshot.runtime.threadId)
      tabsService.upsert(snapshot)
      try {
        model.clearForTests()

        val file = agentChatVirtualFileSystem().findFileByPath(snapshot.tabKey.toPath()) as AgentChatVirtualFile?

        assertThat(file).isNotNull
        assertThat(model.resolve(key)).isNull()
        assertThat(resolveAgentChatThreadPresentation(checkNotNull(file)))
          .isEqualTo(AgentSessionThreadPresentation(title = "Restored thread", activity = AgentThreadActivity.UNREAD))
      }
      finally {
        tabsService.forget(snapshot.tabKey)
      }
    }
  }

  @Test
  fun forgettingTabDoesNotEvictSharedThreadPresentation() {
    timeoutRunBlocking {
      val snapshot = AgentChatTabSnapshot.create(
        projectHash = "hash-1",
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-forget",
        threadId = "thread-forget",
        threadTitle = "Forget me",
        subAgentId = null,
      )
      val tabsService = service<AgentChatTabsService>()
      val model = service<AgentSessionThreadPresentationModel>()
      val key = presentationKey(snapshot.identity.projectPath, AgentSessionProvider.from("codex"), snapshot.runtime.threadId)
      tabsService.upsert(snapshot)
      try {
        model.updateThread(
          path = snapshot.identity.projectPath,
          provider = AgentSessionProvider.from("codex"),
          threadId = snapshot.runtime.threadId,
          title = "Forget me",
          activity = AgentThreadActivity.UNREAD,
        )
        assertThat(model.resolve(key)).isNotNull

        assertThat(tabsService.forget(snapshot.tabKey)).isTrue()
        assertThat(model.resolve(key)).isNotNull
      }
      finally {
        tabsService.forget(snapshot.tabKey)
      }
    }
  }

  @Test
  fun persistsStateInStore() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-empty",
      threadId = "thread-empty",
      threadTitle = "Thread",
      subAgentId = null,
      threadActivity = AgentThreadActivity.UNREAD,
      initialMessageDispatchSteps = listOf(AgentInitialMessageDispatchStep(text = "do not persist this prompt")),
      initialMessageToken = "do-not-persist-token",
      initialMessageSent = false,
    )
    val store = AgentChatTabsStateService(null)
    store.upsert(snapshot)
    try {
      val loaded = store.load(snapshot.tabKey)
      assertThat(loaded).isNotNull
      assertThat(loaded?.identity?.projectPath).isEqualTo(snapshot.identity.projectPath)
      assertThat(loaded?.identity?.threadIdentity).isEqualTo(snapshot.identity.threadIdentity)
      assertThat(loaded?.runtime?.threadId).isEqualTo(snapshot.runtime.threadId)
      assertThat(loaded?.runtime?.threadTitle).isEqualTo(snapshot.runtime.threadTitle)
      assertThat(loaded?.identity?.subAgentId).isEqualTo(snapshot.identity.subAgentId)
      assertThat(loaded?.runtime?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
      assertThat(loaded?.runtime?.initialMessageDispatchSteps).isEmpty()
      assertThat(loaded?.runtime?.initialMessageDispatchStepIndex).isZero()
      assertThat(loaded?.runtime?.initialComposedMessage).isNull()
      assertThat(loaded?.runtime?.initialMessageToken).isNull()
      assertThat(loaded?.runtime?.initialMessageSent).isFalse()
      assertThat(loaded?.runtime?.initialPromptRecord).isNull()
      assertThat(loaded?.runtime?.terminalPromptDispatch).isNull()
    }
    finally {
      store.delete(snapshot.tabKey)
    }
  }

  @Test
  fun restoresPersistedThreadActivityOnLoad() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-status",
      threadId = "thread-status",
      threadTitle = "Thread",
      subAgentId = null,
      threadActivity = AgentThreadActivity.UNREAD,
    )
    val store = AgentChatTabsStateService(null)
    store.upsert(snapshot)
    try {
      val loaded = store.load(snapshot.tabKey)
      assertThat(loaded?.runtime?.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    }
    finally {
      store.delete(snapshot.tabKey)
    }
  }

  @Test
  fun normalizesLegacyIdentityStyleThreadIdOnLoad() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-legacy",
      threadId = "codex:thread-legacy",
      threadTitle = "Thread",
      subAgentId = null,
    )
    val store = AgentChatTabsStateService(null)
    store.upsert(snapshot)
    try {
      val loaded = store.load(snapshot.tabKey)
      assertThat(loaded).isNotNull
      assertThat(loaded?.runtime?.threadId).isEqualTo("codex:thread-legacy")
    }
    finally {
      store.delete(snapshot.tabKey)
    }
  }

  @Test
  fun promotesUnresolvedVirtualFileWhenDescriptorBecomesAvailable() {
    timeoutRunBlocking {
      val snapshot = AgentChatTabSnapshot.create(
        projectHash = "hash-1",
        projectPath = "/work/project-a",
        threadIdentity = "CODEX:thread-9",
        threadId = "thread-9",
        threadTitle = "Thread",
        subAgentId = "alpha",
      )
      val fileSystem = AgentChatVirtualFileSystem()

      val unresolved = fileSystem.getOrCreateFile(AgentChatTabResolution.Unresolved(snapshot.tabKey))
      assertThat(unresolved.projectPath).isBlank()
      assertThat(unresolved.threadIdentity).isBlank()

      val resolved = fileSystem.getOrCreateFile(snapshot)
      assertThat(resolved).isNotSameAs(unresolved)
      assertThat(resolved.projectPath).isEqualTo(snapshot.identity.projectPath)
      assertThat(resolved.threadIdentity).isEqualTo(snapshot.identity.threadIdentity)
      assertThat(resolved.threadId).isEqualTo(snapshot.runtime.threadId)
      assertThat(resolved.subAgentId).isEqualTo(snapshot.identity.subAgentId)
    }
  }

  @Test
  fun deleteByThreadRemovesOnlyMatchingStateEntries() {
    val store = AgentChatTabsStateService(null)
    val matchingBase = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    val matchingSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = "alpha",
    )
    val differentIdentity = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-2",
      threadId = "thread-2",
      threadTitle = "Thread",
      subAgentId = null,
    )
    val differentProject = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-b",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
    )

    store.upsert(matchingBase)
    store.upsert(matchingSubAgent)
    store.upsert(differentIdentity)
    store.upsert(differentProject)
    try {
      val deleted = store.deleteByThread("/work/project-a/", "codex:thread-1")

      assertThat(deleted).isEqualTo(2)
      assertThat(store.load(matchingBase.tabKey)).isNull()
      assertThat(store.load(matchingSubAgent.tabKey)).isNull()
      assertThat(store.load(differentIdentity.tabKey)).isNotNull
      assertThat(store.load(differentProject.tabKey)).isNotNull
    }
    finally {
      store.delete(matchingBase.tabKey)
      store.delete(matchingSubAgent.tabKey)
      store.delete(differentIdentity.tabKey)
      store.delete(differentProject.tabKey)
    }
  }

  @Test
  fun deleteByThreadWithSubAgentRemovesOnlyMatchingSubAgentStateEntries() {
    val store = AgentChatTabsStateService(null)
    val matchingBase = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
    )
    val matchingSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "sub-alpha",
      threadTitle = "Thread",
      subAgentId = "alpha",
    )
    val otherSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "sub-beta",
      threadTitle = "Thread",
      subAgentId = "beta",
    )

    store.upsert(matchingBase)
    store.upsert(matchingSubAgent)
    store.upsert(otherSubAgent)
    try {
      val deleted = store.deleteByThread("/work/project-a", "codex:thread-1", subAgentId = "alpha")

      assertThat(deleted).isEqualTo(1)
      assertThat(store.load(matchingSubAgent.tabKey)).isNull()
      assertThat(store.load(matchingBase.tabKey)).isNotNull
      assertThat(store.load(otherSubAgent.tabKey)).isNotNull
    }
    finally {
      store.delete(matchingBase.tabKey)
      store.delete(matchingSubAgent.tabKey)
      store.delete(otherSubAgent.tabKey)
    }
  }

  @Test
  fun mapsCodexThreadIdentityToCodexIcon() {
    val icon = providerIcon(threadIdentity = "codex:thread-1")

    assertThat(icon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.READY))
  }

  @Test
  fun mapsClaudeThreadIdentityToClaudeIcon() {
    val icon = providerIcon(threadIdentity = "claude:session-1")

    assertThat(icon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("claude"), AgentThreadActivity.READY))
  }

  @Test
  fun usesFallbackIconForUnknownProviderIdentity() {
    val icon = providerIcon(threadIdentity = "unknown:thread-1", threadActivity = AgentThreadActivity.READY)
    val expected = agentSessionThreadStatusIcon(AgentSessionProvider.from("unknown"), AgentThreadActivity.READY)

    assertThat(icon).isSameAs(expected)
  }

  @Test
  fun usesPlainProviderIconForReadyAndBadgesForActiveStates() {
    val readyIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.READY)
    val processingIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.PROCESSING)
    val reviewingIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.REVIEWING)
    val needsInputIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.NEEDS_INPUT)
    val unreadIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.UNREAD)

    assertThat(readyIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.READY))
    assertThat(processingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.PROCESSING))
    assertThat(reviewingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.REVIEWING))
    assertThat(needsInputIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.NEEDS_INPUT))
    assertThat(unreadIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.UNREAD))
    assertThat(readyIcon).isSameAs(AgentWorkbenchCommonIcons.CodexGray)
    assertThat(processingIcon).isNotSameAs(readyIcon)
    assertThat(reviewingIcon).isNotSameAs(readyIcon)
    assertThat(needsInputIcon).isNotSameAs(readyIcon)
    assertThat(unreadIcon).isNotSameAs(readyIcon)
    assertThat(unreadIcon).isNotSameAs(needsInputIcon)
    assertThat(unreadIcon).isNotSameAs(processingIcon)
    assertThat(unreadIcon).isNotSameAs(reviewingIcon)
  }

  @Test
  fun resolvesProviderIconsThroughBridgeRegistry() {
    val customIcon = EmptyIcon.create(18, 18)
    val bridge = ChatTestProviderBridge(
      provider = AgentSessionProvider.from("codex"),
      icon = customIcon,
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      clearAgentChatIconCacheForTests()

      val icon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.PROCESSING)
      val sharedHelperIcon = agentSessionThreadStatusIcon(AgentSessionProvider.from("codex"), AgentThreadActivity.PROCESSING)
      val expected = withAgentThreadActivityBadge(customIcon, AgentThreadActivity.PROCESSING)

      assertThat(icon).isSameAs(sharedHelperIcon)
      assertThat(icon.iconWidth).isEqualTo(expected.iconWidth)
      assertThat(icon.iconHeight).isEqualTo(expected.iconHeight)
      assertThat(icon.iconWidth).isNotEqualTo(AgentWorkbenchCommonIcons.CodexGray.iconWidth)
    }
  }
}

private fun threadModel(
  provider: AgentSessionProvider,
  id: String,
  title: String,
  activity: AgentThreadActivity,
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = 1L,
    archived = false,
    activity = activity,
    provider = provider,
  )
}

private fun presentationKey(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
): AgentSessionThreadPresentationKey {
  return checkNotNull(AgentSessionThreadPresentationKey.create(path, provider, threadId))
}

private fun threadOutlineForkTarget(
  file: AgentChatVirtualFile,
  source: AgentSessionSource,
  item: AgentSessionOutlineItem,
): AgentChatThreadOutlineTarget {
  return AgentChatThreadOutlineTarget(file = file, source = source, item = item)
}

private fun threadOutlineForkActionEvent(
  action: AgentChatThreadOutlineForkAction,
  target: AgentChatThreadOutlineTarget,
): AnActionEvent {
  return TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder()
      .add(AgentChatThreadOutlineDataKeys.SELECTED_TARGET, target)
      .build(),
  )
}

private fun AgentChatThreadOutlineModel.rootNodeTitles(): List<String> {
  return rootIds.map { id -> entriesById.getValue(id).node.title }
}

private fun AgentChatThreadOutlineModel.singleNode(): AgentChatThreadOutlineNode {
  return entriesById.getValue(rootIds.single()).node
}

private fun createThreadOutlinePanel(project: Project): AgentChatThreadOutlinePanel {
  return runInEdtAndGet { AgentChatThreadOutlinePanel(project, startSelectionSubscription = false) }
}

private fun AgentChatThreadOutlinePanel.selectFileForTestsOnEdt(file: AgentChatVirtualFile?) {
  runInEdtAndWait {
    selectFileForTests(file)
  }
}

private fun AgentChatThreadOutlinePanel.modelForTestsOnEdt(): AgentChatThreadOutlineModel {
  return runInEdtAndGet { modelForTests() }
}

private fun AgentChatThreadOutlinePanel.navigateOutlineIdForTestsOnEdt(id: AgentChatThreadOutlineId): Boolean {
  return runInEdtAndGet { navigateOutlineIdForTests(id) }
}

private fun AgentChatThreadOutlinePanel.canShowPopupForOutlineIdForTestsOnEdt(id: AgentChatThreadOutlineId): Boolean {
  return runInEdtAndGet { canShowPopupForOutlineIdForTests(id) }
}

private fun AgentChatThreadOutlinePanel.hasPopupKeyboardActivationForTestsOnEdt(): Boolean {
  return runInEdtAndGet { hasPopupKeyboardActivationForTests() }
}

private fun disposePanel(panel: AgentChatThreadOutlinePanel) {
  runInEdtAndWait {
    Disposer.dispose(panel)
  }
}

private data class OutlineNavigationCall(
  @JvmField val path: String,
  @JvmField val threadId: String,
  @JvmField val itemId: String,
  @JvmField val subAgentId: String?,
  @JvmField val tabKey: String?,
)

private fun testOutline(
  updatedAt: Long,
  items: List<AgentSessionOutlineItem>,
): AgentSessionThreadOutline {
  return AgentSessionThreadOutline(
    provider = AgentSessionProvider.from("codex"),
    threadId = "thread-refresh",
    title = "Refresh thread",
    updatedAt = updatedAt,
    items = items,
  )
}

private fun testOutlineItem(
  id: String,
  kind: AgentSessionOutlineItemKind = AgentSessionOutlineItemKind.USER_PROMPT,
  title: String,
): AgentSessionOutlineItem {
  return AgentSessionOutlineItem(
    id = id,
    kind = kind,
    title = title,
  )
}

private fun testUpdateEvent(path: String = "/work/project-a", threadId: String = "thread-refresh"): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.THREADS_CHANGED,
    scopedPaths = setOf(path),
    threadIds = setOf(threadId),
  )
}

private class ChatTestProviderBridge(
  override val provider: AgentSessionProvider,
  override val icon: Icon,
  private val outlineLoader: suspend (path: String, threadId: String, subAgentId: String?) -> AgentSessionThreadOutline? = { _, _, _ -> null },
  private val activeThreadUpdateEventsProvider: (path: String, threadId: String) -> Flow<AgentSessionSourceUpdateEvent> = { _, _ -> emptyFlow() },
  private val canNavigateOutlineItem: (path: String, threadId: String, itemId: String, subAgentId: String?, tabKey: String?) -> Boolean =
    { _, _, _, _, _ -> false },
  private val navigateOutlineItem: suspend (path: String, threadId: String, itemId: String, subAgentId: String?, tabKey: String?) -> Boolean =
    { _, _, _, _, _ -> false },
  private val canShowForkOutlineItem: (path: String, threadId: String, itemId: String, subAgentId: String?, tabKey: String?) -> Boolean =
    { _, _, _, _, _ -> false },
  private val canForkOutlineItem: (path: String, threadId: String, itemId: String, subAgentId: String?, tabKey: String?) -> Boolean =
    { _, _, _, _, _ -> false },
  private val forkOutlineItem: suspend (project: Project, path: String, threadId: String, itemId: String, subAgentId: String?, tabKey: String?) -> AgentSessionOutlineForkResult? =
    { _, _, _, _, _, _ -> null },
) : AgentSessionProviderDescriptor {
  override val displayNameKey: String
    get() = provider.value

  override val newSessionLabelKey: String
    get() = provider.value

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@ChatTestProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()

    override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline? {
      return outlineLoader(path, threadId, subAgentId)
    }

    override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
      return activeThreadUpdateEventsProvider(path, threadId)
    }

    override fun canNavigateThreadOutlineItem(
      path: String,
      threadId: String,
      itemId: String,
      subAgentId: String?,
      tabKey: String?,
    ): Boolean {
      return canNavigateOutlineItem(path, threadId, itemId, subAgentId, tabKey)
    }

    override suspend fun navigateThreadOutlineItem(
      path: String,
      threadId: String,
      itemId: String,
      subAgentId: String?,
      tabKey: String?,
    ): Boolean {
      return navigateOutlineItem(path, threadId, itemId, subAgentId, tabKey)
    }

    override fun canShowThreadOutlineForkAction(
      path: String,
      threadId: String,
      itemId: String,
      subAgentId: String?,
      tabKey: String?,
    ): Boolean {
      return canShowForkOutlineItem(path, threadId, itemId, subAgentId, tabKey)
    }

    override fun canForkThreadFromOutlineItem(
      path: String,
      threadId: String,
      itemId: String,
      subAgentId: String?,
      tabKey: String?,
    ): Boolean {
      return canForkOutlineItem(path, threadId, itemId, subAgentId, tabKey)
    }

    override suspend fun forkThreadFromOutlineItem(
      project: Project,
      path: String,
      threadId: String,
      itemId: String,
      subAgentId: String?,
      tabKey: String?,
    ): AgentSessionOutlineForkResult? {
      return forkOutlineItem(project, path, threadId, itemId, subAgentId, tabKey)
    }
  }

  override val cliMissingMessageKey: String
    get() = provider.value

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "resume", sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }
}

private fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    Thread.sleep(20)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
