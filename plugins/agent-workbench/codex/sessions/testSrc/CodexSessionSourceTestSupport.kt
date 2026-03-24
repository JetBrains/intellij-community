// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Files
import java.nio.file.Path

internal fun testCreateSource(
  projectDir: Path,
  codexHome: Path,
  threadIds: List<String>,
  appServerHints: Map<String, CodexRefreshHints> = emptyMap(),
): CodexSessionSource {
  val projectPath = projectDir.toString()
  return CodexSessionSource(
    backend = object : CodexSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<CodexBackendThread> {
        return if (path == projectPath) {
          threadIds.mapIndexed { index, threadId ->
            CodexBackendThread(
              thread = CodexThread(
                id = threadId,
                title = "Thread ${index + 1}",
                updatedAt = 100L + index,
                archived = false,
              )
            )
          }
        }
        else {
          emptyList()
        }
      }
    },
    appServerRefreshHintsProvider = testStaticHintsProvider(appServerHints),
    rolloutRefreshHintsProvider = CodexRolloutRefreshHintsProvider(
      rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { codexHome })
    ),
  )
}

internal suspend fun testRefreshHints(
  source: CodexSessionSource,
  projectDir: Path,
  threadIds: List<String>,
): AgentSessionRefreshHints {
  val projectPath = projectDir.toString()
  val listedThreads = source.listThreadsFromClosedProject(projectPath)
  check(listedThreads.map { it.id } == threadIds) {
    "Expected listed thread ids $threadIds but got ${listedThreads.map { it.id }}"
  }

  return source.prefetchRefreshHints(
    paths = listOf(projectPath),
    knownThreadIdsByPath = mapOf(projectPath to threadIds.toSet()),
  ).getOrElse(projectPath) { AgentSessionRefreshHints() }
}

internal suspend fun testRefreshActivities(
  source: CodexSessionSource,
  projectDir: Path,
  threadIds: List<String>,
): Map<String, AgentThreadActivity> {
  return testRefreshHints(source = source, projectDir = projectDir, threadIds = threadIds).activityByThreadId
}

internal suspend fun testRefreshCodexHints(
  projectDir: Path,
  codexHome: Path,
  threadIds: List<String>,
  appServerHints: Map<String, CodexRefreshHints> = emptyMap(),
): CodexRefreshHints {
  val projectPath = projectDir.toString()
  val knownThreadIdsByPath = mapOf(projectPath to threadIds.toSet())
  val rolloutHints = CodexRolloutRefreshHintsProvider(
    rolloutBackend = CodexRolloutSessionBackend(codexHomeProvider = { codexHome })
  ).prefetchRefreshHints(
    paths = listOf(projectPath),
    knownThreadIdsByPath = knownThreadIdsByPath,
  )
  return mergeCodexRefreshHints(
    appServerHintsByPath = appServerHints,
    rolloutHintsByPath = rolloutHints,
  ).getOrElse(projectPath) { CodexRefreshHints() }
}

internal fun testStaticHintsProvider(hintsByPath: Map<String, CodexRefreshHints>): CodexRefreshHintsProvider {
  return object : CodexRefreshHintsProvider {
    override val updates = emptyFlow<Unit>()

    override suspend fun prefetchRefreshHints(
      paths: List<String>,
      knownThreadIdsByPath: Map<String, Set<String>>,
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
): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = activity,
    updatedAt = updatedAt,
    responseRequired = responseRequired,
  )
}

internal fun testCreateProjectDir(root: Path, name: String): Path {
  val projectDir = root.resolve(name)
  Files.createDirectories(projectDir)
  return projectDir
}
