// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AgentChatFileEditorProviderTest {
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
}
