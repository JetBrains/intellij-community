// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
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
  fun updateActivityHintsCarriesChromeActivityAndTimestamp() {
    val model = AgentSessionThreadPresentationModel()

    model.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        AgentSessionThreadActivityPresentationUpdate(
          path = "/work/project",
          threadId = "thread-1",
          activityReport = AgentThreadActivityReport(
            rowActivity = AgentThreadActivity.UNREAD,
            chromeActivity = null,
          ),
          updatedAt = 42L,
        )
      ),
    )

    val key = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    val presentation = model.snapshot()[key]
    assertThat(presentation?.activityReport).isEqualTo(AgentThreadActivityReport(rowActivity = AgentThreadActivity.UNREAD, chromeActivity = null))
    assertThat(presentation?.updatedAt).isEqualTo(42L)
  }

  @Test
  fun olderActivityHintDoesNotRegressNewerProviderSnapshot() {
    val model = AgentSessionThreadPresentationModel()

    model.updateProviderSnapshot(
      provider = AgentSessionProvider.CODEX,
      authoritativePaths = setOf("/work/project"),
      threadsByPath = mapOf(
        "/work/project" to listOf(
          threadModel(
            provider = AgentSessionProvider.CODEX,
            id = "thread-1",
            title = "Thread title",
            activity = AgentThreadActivity.NEEDS_INPUT,
            updatedAt = 100L,
          )
        )
      ),
    )

    model.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        AgentSessionThreadActivityPresentationUpdate(
          path = "/work/project",
          threadId = "thread-1",
          activityReport = AgentThreadActivityReport(AgentThreadActivity.READY),
          updatedAt = 50L,
        )
      ),
    )

    val key = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    val presentation = model.snapshot()[key]
    assertThat(presentation?.activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    assertThat(presentation?.updatedAt).isEqualTo(100L)
  }

  @Test
  fun olderProviderSnapshotDoesNotRegressNewerActivityHint() {
    val model = AgentSessionThreadPresentationModel()

    model.updateActivityHints(
      provider = AgentSessionProvider.CODEX,
      updates = listOf(
        AgentSessionThreadActivityPresentationUpdate(
          path = "/work/project",
          threadId = "thread-1",
          activityReport = AgentThreadActivityReport(AgentThreadActivity.REVIEWING),
          updatedAt = 200L,
        )
      ),
    )

    model.updateProviderSnapshot(
      provider = AgentSessionProvider.CODEX,
      authoritativePaths = setOf("/work/project"),
      threadsByPath = mapOf(
        "/work/project" to listOf(
          threadModel(
            provider = AgentSessionProvider.CODEX,
            id = "thread-1",
            title = "Thread title",
            activity = AgentThreadActivity.READY,
            updatedAt = 100L,
          )
        )
      ),
    )

    val key = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    val presentation = model.snapshot()[key]
    assertThat(presentation?.title).isEqualTo("Thread title")
    assertThat(presentation?.activity).isEqualTo(AgentThreadActivity.REVIEWING)
    assertThat(presentation?.updatedAt).isEqualTo(200L)
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
          threadModel(
            AgentSessionProvider.CODEX,
            "thread-1",
            "Codex thread",
            AgentThreadActivity.READY,
            summaryActivity = null,
            updatedAt = 10L,
          ),
          threadModel(AgentSessionProvider.CLAUDE, "thread-2", "Claude thread", AgentThreadActivity.PROCESSING),
        )
      ),
    )

    val codexKey = presentationKey("/work/project", AgentSessionProvider.CODEX, "thread-1")
    val claudeKey = presentationKey("/work/project", AgentSessionProvider.CLAUDE, "thread-2")
    assertThat(changeSet.changedKeys).containsExactly(codexKey)
    assertThat(changeSet.removedKeys).containsExactly(removedKey)
    assertThat(model.snapshot()).containsOnlyKeys(codexKey)
    assertThat(model.snapshot()[codexKey]?.activityReport).isEqualTo(AgentThreadActivityReport(rowActivity = AgentThreadActivity.READY, chromeActivity = null))
    assertThat(model.snapshot()[codexKey]?.updatedAt).isEqualTo(10L)
    assertThat(model.snapshot()).doesNotContainKey(claudeKey)
  }
}

private fun threadModel(
  provider: AgentSessionProvider,
  id: String,
  title: String,
  activity: AgentThreadActivity,
  summaryActivity: AgentThreadActivity? = activity,
  updatedAt: Long = 1L,
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    activity = activity,
    summaryActivity = summaryActivity,
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
