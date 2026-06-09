// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadActivityPresentationUpdate
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionThreadPresentationModelTest {
  @Test
  fun updateThreadNormalizesKeyAndTitle() {
    val model = AgentSessionThreadPresentationModel()

    val changeSet = model.updateThread(
      path = "/work/project/",
      provider = AgentSessionProvider.CODEX,
      threadId = " thread-1 ",
      title = "  Renamed\n\n  thread  ",
      activity = AgentThreadActivity.PROCESSING,
    )

    val key = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    assertThat(changeSet.changedKeys).containsExactly(key)
    assertThat(model.snapshot()[key]?.title).isEqualTo("Renamed thread")
    assertThat(model.snapshot()[key]?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
  }

  @Test
  fun updateActivityHintsDoesNotFabricateTitles() {
    val model = AgentSessionThreadPresentationModel()

    val changeSet = model.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        AgentSessionThreadActivityPresentationUpdate(
          path = "/work/project",
          threadId = "thread-1",
          activity = AgentThreadActivity.PROCESSING,
        )
      ),
    )

    val key = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    assertThat(changeSet.changedKeys).containsExactly(key)
    assertThat(model.snapshot()[key]?.title).isEmpty()
    assertThat(model.snapshot()[key]?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
  }

  @Test
  fun updateProviderSnapshotFiltersProviderAndRemovesMissingAuthoritativeThreads() {
    val model = AgentSessionThreadPresentationModel()
    val removedKey = presentationKey("/work/project", AgentSessionProvider.CODEX, "removed")
    model.updateThread(
      path = "/work/project",
      provider = AgentSessionProvider.CODEX,
      threadId = "removed",
      title = "Removed",
      activity = AgentThreadActivity.READY,
    )

    val changeSet = model.updateProviderSnapshot(
      provider = AgentSessionProvider.CODEX,
      authoritativePaths = setOf("/work/project"),
      threadsByPath = mapOf(
        "/work/project" to listOf(
          threadModel(AgentSessionProvider.CODEX, "thread-1", "Codex thread", AgentThreadActivity.READY),
          threadModel(AgentSessionProvider.CLAUDE, "thread-2", "Claude thread", AgentThreadActivity.PROCESSING),
        )
      ),
    )

    val codexKey = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    val claudeKey = presentationKey("/work/project", AgentSessionProvider.CLAUDE, "thread-2")
    assertThat(changeSet.changedKeys).containsExactly(codexKey)
    assertThat(changeSet.removedKeys).containsExactly(removedKey)
    assertThat(model.snapshot()).containsOnlyKeys(codexKey)
    assertThat(model.snapshot()).doesNotContainKey(claudeKey)
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
