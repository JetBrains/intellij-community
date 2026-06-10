// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.withAgentThreadActivityBadge
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentation
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.IconManager
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
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
      newThreadRebindRequestedAtMs = 300,
      initialMessageDispatchSteps = dispatchSteps,
      initialMessageDispatchStepIndex = 1,
      initialMessageToken = "token-state",
      initialMessageSent = false,
    )
    val element = Element("state")

    val startupIntent = AgentChatStartupIntent.NewSession(
      provider = AgentSessionProvider.CODEX,
      launchMode = AgentSessionLaunchMode.YOLO,
    )

    writeAgentChatFileEditorState(AgentChatFileEditorState(snapshot = snapshot, startupIntent = startupIntent), element)

    assertThat(element.getAttributeValue("shellCommand")).isNull()
    assertThat(element.getAttributeValue("shellEnvVariables")).isNull()
    assertThat(element.getAttributeValue("startupKind")).isEqualTo("newSession")
    assertThat(element.getAttributeValue("startupProvider")).isEqualTo(AgentSessionProvider.CODEX.value)
    assertThat(element.getAttributeValue("startupLaunchMode")).isEqualTo(AgentSessionLaunchMode.YOLO.name)
    assertThat(element.getAttributeValue("launchMode")).isEqualTo("yolo")

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
    assertThat(restored?.runtime?.newThreadRebindRequestedAtMs).isEqualTo(300)
    assertThat(restored?.runtime?.initialMessageDispatchSteps).containsExactlyElementsOf(dispatchSteps)
    assertThat(restored?.runtime?.initialMessageDispatchStepIndex).isEqualTo(1)
    assertThat(restored?.runtime?.initialMessageToken).isEqualTo("token-state")
    assertThat(restored?.runtime?.initialMessageSent).isFalse()
    assertThat(restoredState.startupIntent).isEqualTo(startupIntent)
    assertThat(file.launchMode).isEqualTo("yolo")
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
        "<applicationService serviceInterface=\"com.intellij.agent.workbench.sessions.core.providers.AgentOpenTopLevelThreadDispatchService\"",
      )
      .contains(
        "serviceImplementation=\"com.intellij.agent.workbench.chat.AgentChatOpenTopLevelThreadDispatchService\"/>",
      )
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
    val refreshedKey = presentationKey("/work/project-a", AgentSessionProvider.CODEX, "thread-1")
    val removedKey = presentationKey("/work/project-a", AgentSessionProvider.CODEX, "thread-2")
    val otherProviderKey = presentationKey("/work/project-a", AgentSessionProvider.CLAUDE, "session-1")
    val otherPathKey = presentationKey("/work/project-b", AgentSessionProvider.CODEX, "thread-3")
    model.replaceForTests(
      mapOf(
        refreshedKey to AgentSessionThreadPresentation(title = "Old title", activity = AgentThreadActivity.READY),
        removedKey to AgentSessionThreadPresentation(title = "Removed title", activity = AgentThreadActivity.UNREAD),
        otherProviderKey to AgentSessionThreadPresentation(title = "Claude title", activity = AgentThreadActivity.PROCESSING),
        otherPathKey to AgentSessionThreadPresentation(title = "Other path", activity = AgentThreadActivity.READY),
      )
    )

    val changeSet = model.updateProviderSnapshot(
      provider = AgentSessionProvider.CODEX,
      authoritativePaths = setOf("/work/project-a/"),
      threadsByPath = mapOf(
        "/work/project-a" to listOf(
          threadModel(AgentSessionProvider.CODEX, "thread-1", "Renamed title", AgentThreadActivity.PROCESSING)
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
  fun sharedThreadPresentationActivityOnlyUpdateKeepsExistingTitle(): Unit = timeoutRunBlocking {
    val model = service<AgentSessionThreadPresentationModel>()
    val key = presentationKey("/work/project-a", AgentSessionProvider.CODEX, "thread-1")
    model.updateThread(
      path = "/work/project-a",
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-1",
      title = "Existing title",
      activity = AgentThreadActivity.READY,
    )

    val changeSet = model.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        com.intellij.agent.workbench.sessions.core.AgentSessionThreadActivityPresentationUpdate(
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

  @Test
  fun restoreMaterializationKeepsPersistedPresentationAsBootstrapFallback(): Unit = timeoutRunBlocking {
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
    val key = presentationKey(snapshot.identity.projectPath, AgentSessionProvider.CODEX, snapshot.runtime.threadId)
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

  @Test
  fun forgettingTabDoesNotEvictSharedThreadPresentation(): Unit = timeoutRunBlocking {
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
    val key = presentationKey(snapshot.identity.projectPath, AgentSessionProvider.CODEX, snapshot.runtime.threadId)
    tabsService.upsert(snapshot)
    try {
      model.updateThread(
        path = snapshot.identity.projectPath,
        provider = AgentSessionProvider.CODEX,
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
  fun promotesUnresolvedVirtualFileWhenDescriptorBecomesAvailable(): Unit = timeoutRunBlocking {
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

    assertThat(icon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.READY))
  }

  @Test
  fun mapsClaudeThreadIdentityToClaudeIcon() {
    val icon = providerIcon(threadIdentity = "claude:session-1")

    assertThat(icon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CLAUDE, AgentThreadActivity.READY))
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

    assertThat(readyIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.READY))
    assertThat(processingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.PROCESSING))
    assertThat(reviewingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.REVIEWING))
    assertThat(needsInputIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.NEEDS_INPUT))
    assertThat(unreadIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.UNREAD))
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
      provider = AgentSessionProvider.CODEX,
      icon = customIcon,
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      clearAgentChatIconCacheForTests()

      val icon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.PROCESSING)
      val sharedHelperIcon = agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.PROCESSING)
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

private class ChatTestProviderBridge(
  override val provider: AgentSessionProvider,
  override val icon: Icon,
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
