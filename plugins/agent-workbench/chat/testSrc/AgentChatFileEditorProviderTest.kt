// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.withAgentThreadActivityBadge
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.IconManager
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
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
    IconManager.deactivate()
    IconLoader.deactivate()
  }

  @Test
  fun keepsCodexResumeCommandOnVirtualFile() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )

    assertThat(file.shellCommand).containsExactly("codex", "resume", "thread-1")
  }

  @Test
  fun startupLaunchSpecOverrideIsConsumedOnceAndNotPersisted() {
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

    val startupLaunchSpec = file.consumeStartupLaunchSpec()
    assertThat(startupLaunchSpec.command).containsExactly("codex", "--", "-run this")
    assertThat(startupLaunchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/custom/bin", "TERM" to "xterm-256color", "DISABLE_AUTOUPDATER" to "1"))

    val fallbackLaunchSpec = file.consumeStartupLaunchSpec()
    assertThat(fallbackLaunchSpec.command).containsExactly("codex", "resume", "thread-1")
    assertThat(fallbackLaunchSpec.envVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/usr/local/bin", "TERM" to "xterm-256color"))
    assertThat(file.toSnapshot().runtime.shellCommand).containsExactly("codex", "resume", "thread-1")
    assertThat(file.toSnapshot().runtime.shellEnvVariables)
      .containsExactlyEntriesOf(mapOf("PATH" to "/usr/local/bin", "TERM" to "xterm-256color"))
  }

  @Test
  fun registersAgentChatFileIconProvider() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.chat.xml")) {
      "Module descriptor intellij.agent.workbench.chat.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<fileIconProvider implementation=\"com.intellij.agent.workbench.chat.AgentChatFileIconProvider\"/>")
  }

  @Test
  fun keepsClaudeResumeCommandOnVirtualFile() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CLAUDE:session-1",
      shellCommand = listOf("claude", "--resume", "session-1"),
      threadId = "session-1",
      threadTitle = "Session One",
      subAgentId = null,
    )

    assertThat(file.shellCommand).containsExactly("claude", "--resume", "session-1")
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
  fun usesLowercaseBase36TabKey() {
    val snapshot = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "thread-42"),
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
    file.updateThreadTitle("Renamed title")
    assertThat(file.path).isEqualTo(originalPath)
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
      shellCommand = emptyList(),
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
      assertThat(loaded?.runtime?.shellCommand).isEqualTo(snapshot.runtime.shellCommand)
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
      shellCommand = listOf("codex", "resume", "thread-status"),
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
      shellCommand = listOf("codex", "resume", "thread-legacy"),
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
      shellCommand = listOf("codex", "resume", "thread-9"),
    )
    val fileSystem = AgentChatVirtualFileSystem()

    val unresolved = fileSystem.getOrCreateFile(AgentChatTabResolution.Unresolved(snapshot.tabKey))
    assertThat(unresolved.projectPath).isBlank()
    assertThat(unresolved.threadIdentity).isBlank()
    assertThat(unresolved.shellCommand).isEmpty()

    val resolved = fileSystem.getOrCreateFile(snapshot)
    assertThat(resolved).isNotSameAs(unresolved)
    assertThat(resolved.projectPath).isEqualTo(snapshot.identity.projectPath)
    assertThat(resolved.threadIdentity).isEqualTo(snapshot.identity.threadIdentity)
    assertThat(resolved.threadId).isEqualTo(snapshot.runtime.threadId)
    assertThat(resolved.subAgentId).isEqualTo(snapshot.identity.subAgentId)
    assertThat(resolved.shellCommand).isEqualTo(snapshot.runtime.shellCommand)
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
      shellCommand = listOf("codex", "resume", "thread-1"),
    )
    val matchingSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "thread-1"),
    )
    val differentIdentity = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-2",
      threadId = "thread-2",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-2"),
    )
    val differentProject = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-b",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-1"),
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
      shellCommand = listOf("codex", "resume", "thread-1"),
    )
    val matchingSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "sub-alpha",
      threadTitle = "Thread",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "sub-alpha"),
    )
    val otherSubAgent = AgentChatTabSnapshot.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "sub-beta",
      threadTitle = "Thread",
      subAgentId = "beta",
      shellCommand = listOf("codex", "resume", "sub-beta"),
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
  fun usesBadgedProviderIconForAllActivities() {
    val readyIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.READY)
    val processingIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.PROCESSING)
    val reviewingIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.REVIEWING)
    val unreadIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.UNREAD)

    assertThat(readyIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.READY))
    assertThat(processingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.PROCESSING))
    assertThat(reviewingIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.REVIEWING))
    assertThat(unreadIcon).isSameAs(agentSessionThreadStatusIcon(AgentSessionProvider.CODEX, AgentThreadActivity.UNREAD))
    assertThat(readyIcon).isNotEqualTo(AgentWorkbenchCommonIcons.Codex_14x14)
    assertThat(processingIcon).isNotSameAs(readyIcon)
    assertThat(reviewingIcon).isNotSameAs(readyIcon)
    assertThat(unreadIcon).isNotSameAs(readyIcon)
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
      assertThat(icon.iconWidth).isNotEqualTo(AgentWorkbenchCommonIcons.Codex_14x14.iconWidth)
    }
  }
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

  override fun isCliAvailable(): Boolean = true

  override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "resume", sessionId))
  }

  override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test"))
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("test", "create", path, mode.name)),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }
}
