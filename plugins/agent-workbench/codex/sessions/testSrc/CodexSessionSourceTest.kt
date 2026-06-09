// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThreadRefreshResult
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
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
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun loadThreadCostsSkipsPendingThreadIdsBeforeBackendRefresh() {
    val refreshRequests = ArrayList<Set<String>>()
    val source = CodexSessionSource(
      backend = object : CodexSessionBackend {
        override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
          error("Pending thread ids should not trigger list fallback")
        }

        override suspend fun refreshThreads(path: String, threadIds: Set<String>, openProject: Project?): CodexBackendThreadRefreshResult {
          refreshRequests += threadIds
          return CodexBackendThreadRefreshResult()
        }
      },
      appServerRefreshHintsProvider = staticHintsProvider(emptyMap()),
      rolloutRefreshHintsProvider = staticHintsProvider(emptyMap()),
    )

    runBlocking(Dispatchers.Default) {
      val costs = source.loadThreadCosts(
        path = PROJECT_PATH,
        threads = listOf(
          AgentSessionThread(
            id = "new-cost",
            title = "Pending Codex thread",
            updatedAt = 100L,
            archived = false,
            provider = AgentSessionProvider.CODEX,
          )
        ),
      )

      assertThat(costs).isEmpty()
      assertThat(refreshRequests).isEmpty()
    }
  }

  @Test
  fun frozenVisibleCodexCostIsReusedAcrossSourceInstancesUntilUpdatedAtChanges() {
    val projectDir = tempDir.resolve("project-frozen-visible")
    Files.createDirectories(projectDir)
    val projectPath = projectDir.toString()
    val rolloutPath = writeCodexSessionSourceRollout(
      threadId = "thread-1",
      projectDir = projectDir,
      fileName = "rollout-thread-1.jsonl",
      inputTokens = 100,
      outputTokens = 0,
    )
    val threadPathIndex = InMemoryCodexThreadPathIndex().apply {
      recordThreads(
        listOf(
          CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 100L,
            archived = false,
            cwd = projectPath,
            path = rolloutPath,
          )
        )
      )
    }
    var updatedAt = 100L

    fun createCostSource(multiplier: Long): CodexSessionSource {
      return CodexSessionSource(
        backend = object : CodexSessionBackend {
          override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
            return listOf(
              CodexBackendThread(
                thread = CodexThread(
                  id = "thread-1",
                  title = "thread-1",
                  updatedAt = updatedAt,
                  archived = false,
                  cwd = projectPath,
                  path = rolloutPath,
                )
              )
            )
          }
        },
        appServerRefreshHintsProvider = staticHintsProvider(emptyMap()),
        rolloutRefreshHintsProvider = staticHintsProvider(emptyMap()),
        rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir }),
        calculateCost = { usage ->
          AgentSessionCost(
            amountUsd = BigDecimal.valueOf(usage.inputTokens * multiplier),
            kind = AgentSessionCostKind.ESTIMATED,
            matchedModelId = usage.modelId,
          )
        },
        threadPathIndex = threadPathIndex,
      )
    }

    runBlocking(Dispatchers.Default) {
      val source1 = createCostSource(multiplier = 1)
      val listed1 = source1.listThreadsFromClosedProject(projectPath)
      val cost1 = source1.loadThreadCosts(projectPath, listed1).getValue("thread-1")
      assertThat(cost1).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(100),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )

      val source2 = createCostSource(multiplier = 9)
      val listed2 = source2.listThreadsFromClosedProject(projectPath)
      val cost2 = source2.loadThreadCosts(projectPath, listed2).getValue("thread-1")
      assertThat(cost2).isEqualTo(cost1)

      updatedAt = 200L
      threadPathIndex.recordThreads(
        listOf(
          CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 200L,
            archived = false,
            cwd = projectPath,
            path = rolloutPath,
          )
        )
      )

      val source3 = createCostSource(multiplier = 2)
      val listed3 = source3.listThreadsFromClosedProject(projectPath)
      val cost3 = source3.loadThreadCosts(projectPath, listed3).getValue("thread-1")
      assertThat(cost3).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(200),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )
    }
  }

  @Test
  fun frozenArchivedCodexCostIsReusedAcrossSourceInstancesUntilUpdatedAtChanges() {
    val projectDir = tempDir.resolve("project-frozen-archived")
    Files.createDirectories(projectDir)
    val projectPath = projectDir.toString()
    val rolloutPath = writeCodexSessionSourceRollout(
      threadId = "archived-1",
      projectDir = projectDir,
      fileName = "rollout-archived-1.jsonl",
      inputTokens = 0,
      outputTokens = 100,
    )
    val threadPathIndex = InMemoryCodexThreadPathIndex().apply {
      recordThreads(
        listOf(
          CodexThread(
            id = "archived-1",
            title = "archived-1",
            updatedAt = 100L,
            archived = true,
            cwd = projectPath,
            path = rolloutPath,
          )
        )
      )
    }
    var updatedAt = 100L

    fun createArchivedCostSource(multiplier: Long): CodexSessionSource {
      return CodexSessionSource(
        backend = object : CodexSessionBackend {
          override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> = emptyList()

          override suspend fun listArchivedThreads(path: String, openProject: Project?): List<CodexBackendThread> {
            return listOf(
              CodexBackendThread(
                thread = CodexThread(
                  id = "archived-1",
                  title = "archived-1",
                  updatedAt = updatedAt,
                  archived = true,
                  cwd = projectPath,
                  path = rolloutPath,
                )
              )
            )
          }
        },
        appServerRefreshHintsProvider = staticHintsProvider(emptyMap()),
        rolloutRefreshHintsProvider = staticHintsProvider(emptyMap()),
        rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { tempDir }),
        calculateCost = { usage ->
          AgentSessionCost(
            amountUsd = BigDecimal.valueOf(usage.outputTokens * multiplier),
            kind = AgentSessionCostKind.ESTIMATED,
            matchedModelId = usage.modelId,
          )
        },
        threadPathIndex = threadPathIndex,
      )
    }

    runBlocking(Dispatchers.Default) {
      val source1 = createArchivedCostSource(multiplier = 1)
      val archived1 = source1.listArchivedThreadsFromClosedProject(projectPath)
      val cost1 = source1.loadThreadCosts(projectPath, archived1).getValue("archived-1")
      assertThat(cost1).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(100),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )

      val source2 = createArchivedCostSource(multiplier = 9)
      val archived2 = source2.listArchivedThreadsFromClosedProject(projectPath)
      assertThat(archived2.single().cost).isEqualTo(cost1)

      updatedAt = 200L
      threadPathIndex.recordThreads(
        listOf(
          CodexThread(
            id = "archived-1",
            title = "archived-1",
            updatedAt = 200L,
            archived = true,
            cwd = projectPath,
            path = rolloutPath,
          )
        )
      )

      val source3 = createArchivedCostSource(multiplier = 2)
      val archived3 = source3.listArchivedThreadsFromClosedProject(projectPath)
      val cost3 = source3.loadThreadCosts(projectPath, archived3).getValue("archived-1")
      assertThat(cost3).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(200),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )
    }
  }

  @Test
  fun loadThreadCostsPrefersExactRolloutPathWithoutBackendRefresh() {
    val projectDir = tempDir.resolve("project-exact-rollout-cost")
    Files.createDirectories(projectDir)
    val projectPath = projectDir.toString()
    val rolloutPath = writeCodexSessionSourceRollout(
      threadId = "thread-1",
      projectDir = projectDir,
      fileName = "rollout-thread-exact.jsonl",
      inputTokens = 100,
      outputTokens = 0,
    )
    val threadPathIndex = InMemoryCodexThreadPathIndex().apply {
      recordThreads(
        listOf(
          CodexThread(
            id = "thread-1",
            title = "thread-1",
            updatedAt = 100L,
            archived = false,
            cwd = projectPath,
            path = rolloutPath,
          )
        )
      )
    }
    var listCalls = 0
    var refreshCalls = 0
    val source = CodexSessionSource(
      backend = object : CodexSessionBackend {
        override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
          listCalls += 1
          return listOf(
            CodexBackendThread(
              thread = CodexThread(
                id = "thread-1",
                title = "thread-1",
                updatedAt = 100L,
                archived = false,
                cwd = projectPath,
                path = rolloutPath,
              )
            )
          )
        }

        override suspend fun refreshThreads(
          path: String,
          threadIds: Set<String>,
          openProject: Project?,
        ): CodexBackendThreadRefreshResult {
          refreshCalls += 1
          return CodexBackendThreadRefreshResult()
        }
      },
      appServerRefreshHintsProvider = staticHintsProvider(emptyMap()),
      rolloutRefreshHintsProvider = staticHintsProvider(emptyMap()),
      rolloutBackend = object : CodexSessionBackend {
        override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
          error("Rollout fallback should not be used when exact rollout path is known")
        }

        override suspend fun refreshThreads(
          path: String,
          threadIds: Set<String>,
          openProject: Project?,
        ): CodexBackendThreadRefreshResult {
          error("Rollout fallback should not be used when exact rollout path is known")
        }
      },
      calculateCost = { usage ->
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(usage.inputTokens),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = usage.modelId,
        )
      },
      threadPathIndex = threadPathIndex,
    )

    runBlocking(Dispatchers.Default) {
      val listedThreads = source.listThreadsFromClosedProject(projectPath)
      val loadedCosts = source.loadThreadCosts(projectPath, listedThreads)

      assertThat(listCalls).isEqualTo(1)
      assertThat(refreshCalls).isZero()
      assertThat(loadedCosts.getValue("thread-1")).isEqualTo(
        AgentSessionCost(
          amountUsd = BigDecimal.valueOf(100),
          kind = AgentSessionCostKind.ESTIMATED,
          matchedModelId = "gpt-5",
        )
      )
    }
  }

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

      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.NEEDS_INPUT))
      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId["thread-1"]).isEqualTo(
        AgentSessionThreadActivityUpdate(
          activityReport = AgentThreadActivityReport(
            rowActivity = AgentThreadActivity.NEEDS_INPUT,
            chromeActivity = AgentThreadActivity.NEEDS_INPUT,
          ),
          updatesChromeActivity = true,
          updatedAt = 100L,
        )
      )
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
  fun archivedThreadsDeferRolloutCostHydrationUntilExplicitLoad() {
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
      val loadedCosts = source.loadThreadCosts(PROJECT_PATH, archivedThreads)

      assertThat(archivedThreads).hasSize(1)
      assertThat(archivedThreads.single().activity).isEqualTo(AgentThreadActivity.READY)
      assertThat(archivedThreads.single().cost).isNull()
      assertThat(loadedCosts.getValue(archivedThreadId)).isEqualTo(
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

      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
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
      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
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
      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
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
      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
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
      assertThat(hints.getValue(PROJECT_PATH).activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity })
        .containsExactlyEntriesOf(mapOf("thread-1" to AgentThreadActivity.PROCESSING))
    }
  }

  @Test
  fun listThreadsUsesAppServerSnapshotWithoutRolloutFallback() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val observedRolloutSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
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
      rolloutRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedRolloutSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 200L,
            )
          )
        ),
      ),
    )

    runBlocking(Dispatchers.Default) {
      val threads = source.listThreadsFromClosedProject(PROJECT_PATH)

      assertThat(observedAppServerSeeds).isEmpty()
      assertThat(observedRolloutSeeds).isEmpty()
      assertThat(threads.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun prefetchThreadsUsesAppServerSnapshotWithoutRolloutFallback() {
    val observedAppServerSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
    val observedRolloutSeeds = mutableListOf<Set<AgentSessionRefreshThreadSeed>>()
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
      rolloutRefreshHintsProvider = recordingHintsProvider(
        observedRefreshThreadSeeds = observedRolloutSeeds,
        hintsByPath = mapOf(
          PROJECT_PATH to refreshHints(
            "thread-1" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 200L,
            )
          )
        ),
      ),
    )

    runBlocking(Dispatchers.Default) {
      val prefetched = source.prefetchThreads(listOf(PROJECT_PATH))

      assertThat(observedAppServerSeeds).isEmpty()
      assertThat(observedRolloutSeeds).isEmpty()
      assertThat(prefetched.getValue(PROJECT_PATH).single().activity).isEqualTo(AgentThreadActivity.READY)
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
  threadPathIndex: CodexThreadPathIndex = InMemoryCodexThreadPathIndex(),
): CodexSessionSource {
  return CodexSessionSource(
    backend = object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> = backendThreads

      override suspend fun listArchivedThreads(path: String, openProject: Project?): List<CodexBackendThread> = archivedBackendThreads

      override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
        return paths.filterTo(LinkedHashSet()) { path -> path == PROJECT_PATH }
          .associateWith { backendThreads }
      }
    },
    appServerRefreshHintsProvider = appServerRefreshHintsProvider,
    rolloutRefreshHintsProvider = rolloutRefreshHintsProvider,
    threadPathIndex = threadPathIndex,
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

private fun CodexSessionSourceTest.writeCodexSessionSourceRollout(
  threadId: String,
  projectDir: Path,
  fileName: String,
  inputTokens: Long,
  outputTokens: Long,
): String {
  val rolloutDir = tempDir.resolve("sessions").resolve("2026").resolve("05").resolve("28")
  val rolloutFile = rolloutDir.resolve(fileName)
  Files.createDirectories(rolloutDir)
  Files.write(
    rolloutFile,
    listOf(
      codexSessionMetaLine(threadId = threadId, cwd = projectDir),
      codexTokenUsageLine(
        model = "gpt-5",
        inputTokens = inputTokens,
        outputTokens = outputTokens,
      ),
    ),
  )
  return rolloutFile.toString()
}

private fun codexSessionMetaLine(threadId: String, cwd: Path): String {
  val timestamp = "2026-05-28T10:00:00.000Z"
  return """{"timestamp":"$timestamp","type":"session_meta","payload":{"id":"$threadId","timestamp":"$timestamp","cwd":"${cwd.toString().replace("\\", "\\\\")}"}}"""
}

private fun codexTokenUsageLine(model: String, inputTokens: Long, outputTokens: Long): String {
  return """{"timestamp":"2026-05-28T10:00:01.000Z","type":"event_msg","payload":{"type":"token_count","model":"$model","info":{"total_token_usage":{"input_tokens":$inputTokens,"cached_input_tokens":0,"output_tokens":$outputTokens,"reasoning_output_tokens":0}}}}"""
}
