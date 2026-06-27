// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexThread
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexBackendThread
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHints
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionBackend
import com.intellij.platform.ai.agent.codex.sessions.backend.toAgentThreadActivity
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutDiscoveryProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.toAgentSessionRefreshThreadSeeds
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Files
import java.nio.file.Path

internal fun testCreateSource(
  projectDir: Path,
  codexHome: Path,
  threadIds: List<String>,
  appServerHints: Map<String, CodexRefreshHints> = emptyMap(),
  backendThreadCustomizer: (CodexBackendThread) -> CodexBackendThread = { it },
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost = {
    AgentSessionCost(amountUsd = null,
                     kind = AgentSessionCostKind.UNAVAILABLE)
  },
): CodexSessionSource {
  val projectPath = projectDir.toString()
  val rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { codexHome })
  val backend = object : CodexSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
      return if (path == projectPath) {
        threadIds.mapIndexed { index, threadId ->
          backendThreadCustomizer(
            CodexBackendThread(
              thread = CodexThread(
                id = threadId,
                title = "Thread ${index + 1}",
                updatedAt = 100L + index,
                archived = false,
              )
            )
          )
        }
      }
      else {
        emptyList()
      }
    }
  }
  val appServerRefreshHintsProvider = testStaticHintsProvider(appServerHints)
  val rolloutDiscoveryProvider = CodexRolloutDiscoveryProvider(rolloutBackend = rolloutBackend)
  return CodexSessionSource(
    backend,
    appServerRefreshHintsProvider,
    rolloutDiscoveryProvider,
    rolloutBackend,
    calculateCost,
  )
}

internal suspend fun testRefreshHints(
  source: CodexSessionSource,
  projectDir: Path,
  threadIds: List<String>,
): AgentSessionRefreshHints {
  val projectPath = projectDir.toString()
  val listedThreads = source.listThreads(projectPath, openProject = null)
  check(listedThreads.map { it.id } == threadIds) {
    "Expected listed thread ids $threadIds but got ${listedThreads.map { it.id }}"
  }

  return source.prefetchRefreshHints(
    paths = listOf(projectPath),
    refreshThreadSeedsByPath = mapOf(projectPath to threadIds.toSet().toAgentSessionRefreshThreadSeeds()),
  ).getOrElse(projectPath) { AgentSessionRefreshHints() }
}

internal suspend fun testRefreshActivities(
  source: CodexSessionSource,
  projectDir: Path,
  threadIds: List<String>,
): Map<String, AgentThreadActivity> {
  return testRefreshHints(source = source, projectDir = projectDir, threadIds = threadIds)
    .activityUpdatesByThreadId
    .mapValues { (_, update) -> checkNotNull(update.activityReport.rowActivity) }
}

internal suspend fun testRolloutCodexHints(
  projectDir: Path,
  codexHome: Path,
  threadIds: List<String>,
): CodexRefreshHints {
  val projectPath = projectDir.toString()
  val requestedThreadIds = threadIds.toSet()
  val rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { codexHome })
  val rolloutThreads = if (requestedThreadIds.isEmpty()) {
    rolloutBackend.listThreads(path = projectPath, openProject = null)
  }
  else {
    rolloutBackend.refreshThreads(path = projectPath, threadIds = requestedThreadIds, openProject = null)?.threads.orEmpty()
  }
  return CodexRefreshHints(
    activityHintsByThreadId = rolloutThreads.asSequence()
      .filter { thread -> requestedThreadIds.isEmpty() || thread.thread.id in requestedThreadIds }
      .associate { thread ->
        thread.thread.id to CodexRefreshActivityHint(
          activity = thread.activity.toAgentThreadActivity(),
          updatedAt = thread.thread.updatedAt,
          responseRequired = thread.requiresResponse,
          summaryActivity = thread.summaryActivity?.toAgentThreadActivity(),
        )
      }
  )
}

internal fun testStaticHintsProvider(hintsByPath: Map<String, CodexRefreshHints>): CodexRefreshHintsProvider {
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

internal fun testRefreshHintsOf(vararg entries: Pair<String, CodexRefreshActivityHint>): CodexRefreshHints {
  return CodexRefreshHints(activityHintsByThreadId = linkedMapOf(*entries))
}

internal fun testRefreshHint(
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

internal fun testCreateProjectDir(root: Path, name: String): Path {
  val projectDir = root.resolve(name)
  Files.createDirectories(projectDir)
  return projectDir
}
