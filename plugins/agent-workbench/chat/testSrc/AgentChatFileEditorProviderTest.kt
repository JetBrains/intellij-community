// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.IconManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgentChatFileEditorProviderTest {
  @BeforeEach
  fun setUp() {
    clearAgentChatIconCacheForTests()
    IconManager.activate(null)
  }

  @AfterEach
  fun tearDown() {
    clearAgentChatIconCacheForTests()
    IconManager.deactivate()
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
  fun promotesUnresolvedVirtualFileWhenDescriptorBecomesAvailable() {
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
    assertThat(resolved).isSameAs(unresolved)
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
  fun mapsCodexThreadIdentityToCodexIcon() {
    val icon = providerIcon(threadIdentity = "codex:thread-1")

    assertThat(icon).isNotEqualTo(AllIcons.Toolwindows.ToolWindowMessages)
  }

  @Test
  fun mapsClaudeThreadIdentityToClaudeIcon() {
    val icon = providerIcon(threadIdentity = "claude:session-1")

    assertThat(icon).isNotEqualTo(AllIcons.Toolwindows.ToolWindowMessages)
  }

  @Test
  fun usesFallbackIconForUnknownProviderIdentity() {
    val icon = providerIcon(threadIdentity = "unknown:thread-1", threadActivity = AgentThreadActivity.READY)

    assertThat(icon).isEqualTo(AllIcons.Toolwindows.ToolWindowMessages)
  }

  @Test
  fun usesUnbadgedProviderIconForReadyAndBadgedIconForNonReadyActivity() {
    val readyIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.READY)
    val unreadIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.UNREAD)

    assertThat(readyIcon).isEqualTo(AgentWorkbenchCommonIcons.Codex_14x14)
    assertThat(unreadIcon).isNotEqualTo(readyIcon)
  }
}
