// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.toAgentSessionRefreshThreadSeeds
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.math.BigDecimal

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceTest {
  @Test
  fun markThreadAsReadSuppressesStaleUnreadRefreshHints() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 100L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.markThreadAsRead(threadId = "thread-1", updatedAt = 100L)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(hints).isEmpty()
    }
  }

  @Test
  fun responseRequiredNeedsInputRefreshHintsSurviveMarkAsRead() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.NEEDS_INPUT,
            updatedAt = 100L,
            responseRequired = true,
          )
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.markThreadAsRead(threadId = "thread-1", updatedAt = 100L)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.NEEDS_INPUT))
    }
  }

  @Test
  fun activeThreadSuppressesCurrentUnreadOutputHints() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 100L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.setActiveThreadId("thread-1")

      val whileActive = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )
      assertThat(whileActive).isEmpty()

      source.setActiveThreadId(null)

      val afterDeactivation = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )
      assertThat(afterDeactivation).isEmpty()
    }
  }

  @Test
  fun activeThreadAutoAdvancesTrackerFromListedThreads() {
    val source = createSource(
      backendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 200L,
            archived = false,
          )
        )
      ),
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 200L)
        )
      )
    )

    runBlocking(Dispatchers.Default) {
      source.setActiveThreadId("thread-1")
      source.listThreadsFromClosedProject(PROJECT_PATH)

      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(hints).isEmpty()
    }
  }

  @Test
  fun archivedThreadsComeFromArchivedBackendListWithoutRolloutFallback() {
    val source = createSource(
      archivedBackendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "archived-1",
            title = "Archived 1",
            updatedAt = 200L,
            archived = true,
          ),
          activity = CodexSessionActivity.READY,
        )
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "archived-1" to refreshHint(activity = AgentThreadActivity.PROCESSING, updatedAt = 300L)
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val archivedThreads = source.listArchivedThreadsFromClosedProject(PROJECT_PATH)

      assertThat(archivedThreads).hasSize(1)
      assertThat(archivedThreads.single().id).isEqualTo("archived-1")
      assertThat(archivedThreads.single().archived).isTrue()
      assertThat(archivedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun archivedThreadsUseRolloutUsageSnapshotsForCostWhenBackendDoesNotProvideThem() {
    val archivedThreadId = "archived-1"
    val source = CodexSessionSource(
      backend = object : CodexSessionBackend {
        override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> = emptyList()

        override suspend fun listArchivedThreads(path: String, openProject: Project?): List<CodexBackendThread> {
          return listOf(
            CodexBackendThread(
              thread = CodexThread(
                id = archivedThreadId,
                title = "Archived 1",
                updatedAt = 200L,
                archived = true,
              ),
              activity = CodexSessionActivity.READY,
            )
          )
        }
      },
      appServerRefreshHintsProvider = staticHintsProvider(emptyMap()),
      rolloutRefreshHintsProvider = staticHintsProvider(emptyMap()),
      rolloutBackend = object : CodexSessionBackend {
        override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
          return listOf(
            CodexBackendThread(
              thread = CodexThread(
                id = archivedThreadId,
                title = "Archived 1",
                updatedAt = 200L,
                archived = true,
              ),
              usageSnapshots = listOf(
                AgentSessionUsageSnapshot(
                  modelId = "gpt-5",
                  inputTokens = 10,
                  outputTokens = 5,
                )
              ),
            )
          )
        }
      },
      calculateCost = { usage ->
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(usage.inputTokens + usage.outputTokens),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = usage.modelId,
        )
      },
    )

    runBlocking(Dispatchers.Default) {
      val archivedThreads = source.listArchivedThreadsFromClosedProject(PROJECT_PATH)

      assertThat(archivedThreads).hasSize(1)
      assertThat(archivedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(archivedThreads.single().cost).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(15),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )
    }
  }

  @Test
  fun rolloutWorkingHintOverridesCachedReadyOutputHints() {
    val source = createSource(
      appServerHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.READY,
            updatedAt = 100L,
          )
        )
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun rolloutWorkingHintOverridesFreshAppServerVerificationWhenPrefetchingHints() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
              verifiedFresh = true,
            )
          )
        ),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun rolloutResponseRequiredHintUsesFreshAppServerVerificationWhenPrefetchingHints() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
              verifiedFresh = true,
            )
          )
        ),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.NEEDS_INPUT,
            updatedAt = 200L,
            responseRequired = true,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.READY))
    }
  }

  @Test
  fun rolloutWorkingHintOverridesStaleAppServerVerificationWhenPrefetchingHints() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 100L,
              verifiedFresh = true,
            )
          )
        ),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun rolloutWorkingHintStillAppliesWhenFreshAppServerVerificationUnavailable() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = emptyMap(),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val hints = source.prefetchRefreshHints(
        paths = listOf(PROJECT_PATH),
        refreshThreadSeedsByPath = mapOf(PROJECT_PATH to setOf("thread-1").toAgentSessionRefreshThreadSeeds()),
      )

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(hints.getValue(PROJECT_PATH).activityByThreadId)
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun listThreadsKeepsRolloutWorkingHintOverFreshAppServerVerification() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      backendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 100L,
            archived = false,
          ),
          activity = CodexSessionActivity.PROCESSING,
        )
      ),
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
              verifiedFresh = true,
            )
          )
        ),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val threads = source.listThreadsFromClosedProject(PROJECT_PATH)

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(threads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun listThreadsAppliesNewerRolloutWorkingHintAfterStaleAppServerVerification() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val source = createSource(
      backendThreads = listOf(
        CodexBackendThread(
          thread = CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 100L,
            archived = false,
          ),
          activity = CodexSessionActivity.READY,
        )
      ),
      appServerRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedAppServerSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 100L,
              verifiedFresh = true,
            )
          )
        ),
      ),
      rolloutHints = mapOf(
        PROJECT_PATH to refreshHints(
          "thread-1" to refreshHint(
            activity = AgentThreadActivity.PROCESSING,
            updatedAt = 200L,
          )
        )
      ),
    )

    runBlocking(Dispatchers.Default) {
      val threads = source.listThreadsFromClosedProject(PROJECT_PATH)

      assertThat(observedAppServerSeeds.single().single().forceRefresh).isTrue()
      assertThat(threads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }
}

private const val PROJECT_PATH = "/work/project"

private fun createSource(
  backendThreads: List<CodexBackendThread> = emptyList(),
  archivedBackendThreads: List<CodexBackendThread> = emptyList(),
  appServerHints: Map<String, CodexRefreshHints> = emptyMap(),
  rolloutHints: Map<String, CodexRefreshHints> = emptyMap(),
  appServerRefreshHintsProvider: CodexRefreshHintsProvider = staticHintsProvider(appServerHints),
  rolloutRefreshHintsProvider: CodexRefreshHintsProvider = staticHintsProvider(rolloutHints),
): CodexSessionSource {
  return CodexSessionSource(
    backend = object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> = backendThreads

      override suspend fun listArchivedThreads(path: String, openProject: Project?): List<CodexBackendThread> = archivedBackendThreads
    },
    appServerRefreshHintsProvider = appServerRefreshHintsProvider,
    rolloutRefreshHintsProvider = rolloutRefreshHintsProvider,
  )
}

private fun staticHintsProvider(hintsByPath: Map<String, CodexRefreshHints>): CodexRefreshHintsProvider {
  return object : CodexRefreshHintsProvider {
    override val updateEvents = emptyFlow<AgentSessionSourceUpdateEvent>()

    override suspend fun prefetchRefreshHints(
      paths: List<String>,
      refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
    ): Map<String, CodexRefreshHints> {
      return hintsByPath.filterKeys(paths::contains)
    }
  }
}

private fun recordingHintsProvider(
  observedRefreshThreadSeeds: MutableList<Set<AgentSessionRefreshThreadSeed>>,
  hintsByPath: Map<String, CodexRefreshHints>,
): CodexRefreshHintsProvider {
  return object : CodexRefreshHintsProvider {
    override val updateEvents = emptyFlow<AgentSessionSourceUpdateEvent>()

    override suspend fun prefetchRefreshHints(
      paths: List<String>,
      refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
    ): Map<String, CodexRefreshHints> {
      observedRefreshThreadSeeds += refreshThreadSeedsByPath.getValue(PROJECT_PATH)
      return hintsByPath.filterKeys(paths::contains)
    }
  }
}

private fun refreshHints(vararg entries: Pair<String, CodexRefreshActivityHint>): CodexRefreshHints {
  return CodexRefreshHints(activityHintsByThreadId = linkedMapOf(*entries))
}

private fun refreshHint(
  activity: AgentThreadActivity,
  updatedAt: Long,
  responseRequired: Boolean = false,
  verifiedFresh: Boolean = false,
): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = activity,
    updatedAt = updatedAt,
    responseRequired = responseRequired,
    verifiedFresh = verifiedFresh,
  )
}
