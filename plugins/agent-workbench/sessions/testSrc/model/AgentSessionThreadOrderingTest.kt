// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.model

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionThreadOrderingTest {
  @Test
  fun sortsInitialThreadsByUpdatedTimeProviderAndId() {
    val threads = listOf(
      thread(id = "codex-b", updatedAt = 100, provider = AgentSessionProvider.CODEX),
      thread(id = "claude-a", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
      thread(id = "codex-a", updatedAt = 200, provider = AgentSessionProvider.CODEX),
    )

    val sorted = sortAgentSessionThreadsForDisplay(threads)

    assertThat(sorted.map { it.id }).containsExactly("codex-a", "claude-a", "codex-b")
  }

  @Test
  fun mergePreservesExistingOrderWhenThreadContentChanges() {
    val previousThreads = listOf(
      thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "claude-1", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
    )
    val refreshedThreads = listOf(
      thread(id = "claude-1", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
      thread(id = "codex-2", updatedAt = 900, title = "Updated", provider = AgentSessionProvider.CODEX),
      thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )

    val merged = mergeAgentSessionThreadsForDisplay(previousThreads, refreshedThreads)

    assertThat(merged.map { it.id }).containsExactly("codex-1", "codex-2", "claude-1")
    assertThat(merged.first { it.id == "codex-2" }.title).isEqualTo("Updated")
    assertThat(merged.first { it.id == "codex-2" }.updatedAt).isEqualTo(900)
  }

  @Test
  fun mergeRemovesMissingThreadsWithoutReorderingSurvivors() {
    val previousThreads = listOf(
      thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "claude-1", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
    )
    val refreshedThreads = listOf(
      thread(id = "claude-1", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
      thread(id = "codex-1", updatedAt = 400, provider = AgentSessionProvider.CODEX),
    )

    val merged = mergeAgentSessionThreadsForDisplay(previousThreads, refreshedThreads)

    assertThat(merged.map { it.id }).containsExactly("codex-1", "claude-1")
    assertThat(merged.first { it.id == "codex-1" }.updatedAt).isEqualTo(400)
  }

  @Test
  fun mergeInsertsNewThreadsByDisplayPriorityWithoutReorderingExistingThreads() {
    val previousThreads = listOf(
      thread(id = "old-low", updatedAt = 100, provider = AgentSessionProvider.CODEX),
      thread(id = "old-high", updatedAt = 300, provider = AgentSessionProvider.CODEX),
    )
    val refreshedThreads = listOf(
      thread(id = "old-high", updatedAt = 300, provider = AgentSessionProvider.CODEX),
      thread(id = "new-middle", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
      thread(id = "new-top", updatedAt = 400, provider = AgentSessionProvider.CODEX),
      thread(id = "old-low", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )

    val merged = mergeAgentSessionThreadsForDisplay(previousThreads, refreshedThreads)

    assertThat(merged.map { it.id }).containsExactly("new-top", "new-middle", "old-low", "old-high")
  }

  @Test
  fun mergeOrdersNewThreadsDeterministicallyWhenUpdatedTimesAreEqual() {
    val previousThreads = listOf(
      thread(id = "existing", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val refreshedThreads = listOf(
      thread(id = "new-codex-b", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "existing", updatedAt = 100, provider = AgentSessionProvider.CODEX),
      thread(id = "new-claude", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
      thread(id = "new-codex-a", updatedAt = 200, provider = AgentSessionProvider.CODEX),
    )

    val merged = mergeAgentSessionThreadsForDisplay(previousThreads, refreshedThreads)

    assertThat(merged.map { it.id }).containsExactly("new-claude", "new-codex-a", "new-codex-b", "existing")
  }

  private fun thread(
    id: String,
    updatedAt: Long,
    title: String = id,
    provider: AgentSessionProvider,
    activity: AgentThreadActivity = AgentThreadActivity.READY,
  ): AgentSessionThread {
    return AgentSessionThread(
      id = id,
      title = title,
      updatedAt = updatedAt,
      archived = false,
      activity = activity,
      provider = provider,
    )
  }
}
