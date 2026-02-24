// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.milliseconds

internal const val PROJECT_PATH = "/work/project-a"
internal const val WORKTREE_PATH = "/work/project-feature"

internal class ScriptedSessionSource(
  override val provider: AgentSessionProvider,
  override val canReportExactThreadCount: Boolean = true,
  override val supportsUpdates: Boolean = false,
  override val updates: Flow<Unit> = emptyFlow(),
  private val listFromOpenProject: suspend (path: String, project: Project) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val listFromClosedProject: suspend (path: String) -> List<AgentSessionThread> = { _ -> emptyList() },
  private val prefetch: suspend (paths: List<String>) -> Map<String, List<AgentSessionThread>> = { emptyMap() },
) : AgentSessionSource {
  override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listFromOpenProject(path, project)
  }

  override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listFromClosedProject(path)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    return prefetch(paths)
  }
}

internal fun thread(
  id: String,
  updatedAt: Long,
  provider: AgentSessionProvider,
  title: String = id,
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = provider,
  )
}

internal suspend fun withService(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  treeUiState: SessionsTreeUiState = InMemorySessionsTreeUiState(),
  archiveChatCleanup: suspend (projectPath: String, threadIdentity: String) -> Unit = { _, _ -> },
  action: suspend (AgentSessionsService) -> Unit,
) {
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  try {
    val service = AgentSessionsService(
      serviceScope = scope,
      sessionSourcesProvider = sessionSourcesProvider,
      projectEntriesProvider = projectEntriesProvider,
      treeUiState = treeUiState,
      archiveChatCleanup = archiveChatCleanup,
      subscribeToProjectLifecycle = false,
    )
    action(service)
  }
  finally {
    scope.cancel()
  }
}

internal fun openProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
): ProjectEntry {
  return ProjectEntry(
    path = path,
    name = name,
    project = openProjectProxy(name),
    worktreeEntries = worktrees,
  )
}

internal fun closedProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
): ProjectEntry {
  return ProjectEntry(
    path = path,
    name = name,
    project = null,
    worktreeEntries = worktrees,
  )
}

private fun openProjectProxy(name: String): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
      "isOpen" -> true
      "isDisposed" -> false
      "toString" -> "MockProject($name)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    Project::class.java.classLoader,
    arrayOf(Project::class.java),
    handler,
  ) as Project
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}

internal suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
