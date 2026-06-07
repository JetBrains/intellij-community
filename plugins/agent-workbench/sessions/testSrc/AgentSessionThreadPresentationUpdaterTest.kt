// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.service.AgentSessionThreadActivityPresentationUpdate
import com.intellij.agent.workbench.sessions.service.DefaultAgentSessionThreadPresentationUpdater
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionThreadPresentationUpdaterTest {
  @Test
  fun updateThreadBuildsIdentityKeyAndPreservesActivity(): Unit = runBlocking(Dispatchers.Default) {
    val lowLevelUpdates = mutableListOf<LowLevelPresentationUpdate>()
    val updater = DefaultAgentSessionThreadPresentationUpdater { provider, refreshedPaths, titleMap, activityMap ->
      lowLevelUpdates += LowLevelPresentationUpdate(provider, refreshedPaths, titleMap, activityMap)
      1
    }

    val updated = updater.updateThread(
      provider = AgentSessionProvider.CODEX,
      path = "/work/project",
      threadId = "thread-1",
      title = "Renamed thread",
      activity = AgentThreadActivity.PROCESSING,
    )

    val expectedKey = "/work/project" to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1")
    assertThat(updated).isEqualTo(1)
    assertThat(lowLevelUpdates).containsExactly(
      LowLevelPresentationUpdate(
        provider = AgentSessionProvider.CODEX,
        refreshedPaths = emptySet(),
        titleMap = mapOf(expectedKey to "Renamed thread"),
        activityMap = mapOf(expectedKey to AgentThreadActivity.PROCESSING),
      )
    )
  }

  @Test
  fun updateActivityHintsDoesNotFabricateTitles(): Unit = runBlocking(Dispatchers.Default) {
    val lowLevelUpdates = mutableListOf<LowLevelPresentationUpdate>()
    val updater = DefaultAgentSessionThreadPresentationUpdater { provider, refreshedPaths, titleMap, activityMap ->
      lowLevelUpdates += LowLevelPresentationUpdate(provider, refreshedPaths, titleMap, activityMap)
      1
    }

    updater.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        AgentSessionThreadActivityPresentationUpdate(
          path = "/work/project",
          threadId = "thread-1",
          activity = AgentThreadActivity.PROCESSING,
        )
      ),
    )

    val expectedKey = "/work/project" to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1")
    val update = lowLevelUpdates.single()
    assertThat(update.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(update.refreshedPaths).isEmpty()
    assertThat(update.titleMap).isEmpty()
    assertThat(update.activityMap).containsExactlyEntriesOf(mapOf(expectedKey to AgentThreadActivity.PROCESSING))
  }

  @Test
  fun updateProviderSnapshotFiltersWrongProviderThreads(): Unit = runBlocking(Dispatchers.Default) {
    val lowLevelUpdates = mutableListOf<LowLevelPresentationUpdate>()
    val updater = DefaultAgentSessionThreadPresentationUpdater { provider, refreshedPaths, titleMap, activityMap ->
      lowLevelUpdates += LowLevelPresentationUpdate(provider, refreshedPaths, titleMap, activityMap)
      2
    }

    val updated = updater.updateProviderSnapshot(
      provider = AgentSessionProvider.CODEX,
      authoritativePaths = setOf("/work/project"),
      threadsByPath = mapOf(
        "/work/project" to listOf(
          threadModel(AgentSessionProvider.CODEX, "thread-1", "Codex thread", AgentThreadActivity.READY),
          threadModel(AgentSessionProvider.CLAUDE, "thread-2", "Claude thread", AgentThreadActivity.PROCESSING),
        )
      ),
    )

    val expectedKey = "/work/project" to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1")
    val update = lowLevelUpdates.single()
    assertThat(updated).isEqualTo(2)
    assertThat(update.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(update.refreshedPaths).containsExactly("/work/project")
    assertThat(update.titleMap).containsExactlyEntriesOf(mapOf(expectedKey to "Codex thread"))
    assertThat(update.activityMap).containsExactlyEntriesOf(mapOf(expectedKey to AgentThreadActivity.READY))
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

private data class LowLevelPresentationUpdate(
  val provider: AgentSessionProvider,
  @JvmField val refreshedPaths: Set<String>,
  @JvmField val titleMap: Map<Pair<String, String>, String>,
  @JvmField val activityMap: Map<Pair<String, String>, AgentThreadActivity>,
)
