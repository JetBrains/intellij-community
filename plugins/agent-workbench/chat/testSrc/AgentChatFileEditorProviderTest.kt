// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.PathManager
import com.intellij.ui.IconManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class AgentChatFileEditorProviderTest {
  @Before
  fun setUp() {
    IconManager.activate(null)
  }

  @After
  fun tearDown() {
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
    val tabKey = AgentChatFileDescriptor.parsePath(file.path)
    assertThat(tabKey).isNotNull
    assertThat(tabKey).isEqualTo(file.tabKey)
    assertThat(file.path).startsWith("2/")
  }

  @Test
  fun usesLowercaseBase36TabKey() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "thread-42"),
    )

    val uppercasePath = "2/${descriptor.tabKey.uppercase()}"
    val truncatedPath = "2/${descriptor.tabKey.dropLast(1)}"

    assertThat(descriptor.tabKey).matches("[0-9a-z]{50}")
    assertThat(AgentChatFileDescriptor.parsePath("2/${descriptor.tabKey}")).isEqualTo(descriptor.tabKey)
    assertThat(AgentChatFileDescriptor.parsePath(uppercasePath)).isNull()
    assertThat(AgentChatFileDescriptor.parsePath(truncatedPath)).isNull()
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
  fun persistsMetadataInStore() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-empty",
      threadId = "thread-empty",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = emptyList(),
    )
    val store = AgentChatTabMetadataStores.createStandaloneForTest()
    store.upsert(descriptor)
    try {
      val loaded = store.loadDescriptor(descriptor.tabKey)
      assertThat(loaded).isNotNull
      assertThat(loaded?.projectPath).isEqualTo(descriptor.projectPath)
      assertThat(loaded?.threadIdentity).isEqualTo(descriptor.threadIdentity)
      assertThat(loaded?.threadId).isEqualTo(descriptor.threadId)
      assertThat(loaded?.threadTitle).isEqualTo(descriptor.threadTitle)
      assertThat(loaded?.subAgentId).isEqualTo(descriptor.subAgentId)
      assertThat(loaded?.shellCommand).isEqualTo(descriptor.shellCommand)
    }
    finally {
      store.delete(descriptor.tabKey)
    }
  }

  @Test
  fun normalizesLegacyIdentityStyleThreadIdOnLoad() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-legacy",
      threadId = "codex:thread-legacy",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-legacy"),
    )
    val store = AgentChatTabMetadataStores.createStandaloneForTest()
    store.upsert(descriptor)
    try {
      val loaded = store.loadDescriptor(descriptor.tabKey)
      assertThat(loaded).isNotNull
      assertThat(loaded?.threadId).isEqualTo("thread-legacy")
    }
    finally {
      store.delete(descriptor.tabKey)
    }
  }

  @Test
  fun promotesUnresolvedVirtualFileWhenDescriptorBecomesAvailable() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-9",
      threadId = "thread-9",
      threadTitle = "Thread",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "thread-9"),
    )
    val fileSystem = AgentChatVirtualFileSystem()

    val unresolved = fileSystem.getOrCreateFile(AgentChatFileDescriptor.unresolved(descriptor.tabKey))
    assertThat(unresolved.projectPath).isBlank()
    assertThat(unresolved.threadIdentity).isBlank()
    assertThat(unresolved.shellCommand).isEmpty()

    val resolved = fileSystem.getOrCreateFile(descriptor)
    assertThat(resolved).isSameAs(unresolved)
    assertThat(resolved.projectPath).isEqualTo(descriptor.projectPath)
    assertThat(resolved.threadIdentity).isEqualTo(descriptor.threadIdentity)
    assertThat(resolved.threadId).isEqualTo(descriptor.threadId)
    assertThat(resolved.subAgentId).isEqualTo(descriptor.subAgentId)
    assertThat(resolved.shellCommand).isEqualTo(descriptor.shellCommand)
  }

  @Test
  fun deleteByThreadRemovesOnlyMatchingMetadataFiles() {
    val store = AgentChatTabMetadataStores.createStandaloneForTest()
    val matchingBase = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-1"),
    )
    val matchingSubAgent = matchingBase.copy(tabKey = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      threadTitle = "Thread",
      subAgentId = "alpha",
      shellCommand = listOf("codex", "resume", "thread-1"),
    ).tabKey, subAgentId = "alpha")
    val differentIdentity = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-2",
      threadId = "thread-2",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-2"),
    )
    val differentProject = AgentChatFileDescriptor.create(
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
      assertThat(store.loadDescriptor(matchingBase.tabKey)).isNull()
      assertThat(store.loadDescriptor(matchingSubAgent.tabKey)).isNull()
      assertThat(store.loadDescriptor(differentIdentity.tabKey)).isNotNull
      assertThat(store.loadDescriptor(differentProject.tabKey)).isNotNull
    }
    finally {
      store.delete(matchingBase.tabKey)
      store.delete(matchingSubAgent.tabKey)
      store.delete(differentIdentity.tabKey)
      store.delete(differentProject.tabKey)
    }
  }

  @Test
  fun deleteByThreadRemovesVersionMismatchedMetadataFile() {
    val descriptor = AgentChatFileDescriptor.create(
      projectHash = "hash-1",
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-legacy",
      threadId = "thread-legacy",
      threadTitle = "Thread",
      subAgentId = null,
      shellCommand = listOf("codex", "resume", "thread-legacy"),
    )
    val store = AgentChatTabMetadataStores.createStandaloneForTest()
    store.upsert(descriptor)
    val metadataPath = PathManager.getConfigDir()
      .resolve("agent-workbench-chat-frame")
      .resolve("tabs")
      .resolve("${descriptor.tabKey}.awchat.json")
    val originalJson = Files.readString(metadataPath)
    Files.writeString(metadataPath, originalJson.replace("\"version\":2", "\"version\":99"))
    try {
      val deleted = store.deleteByThread("/work/project-a", "codex:thread-legacy")

      assertThat(deleted).isEqualTo(1)
      assertThat(store.loadDescriptor(descriptor.tabKey)).isNull()
    }
    finally {
      store.delete(descriptor.tabKey)
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
    val icon = providerIcon(threadIdentity = "unknown:thread-1")

    assertThat(icon).isNotEqualTo(AllIcons.Toolwindows.ToolWindowMessages)
  }

  @Test
  fun addsDistinctBadgesForDifferentThreadActivities() {
    val readyIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.READY)
    val unreadIcon = providerIcon(threadIdentity = "codex:thread-1", threadActivity = AgentThreadActivity.UNREAD)

    assertThat(unreadIcon).isNotEqualTo(readyIcon)
  }
}
