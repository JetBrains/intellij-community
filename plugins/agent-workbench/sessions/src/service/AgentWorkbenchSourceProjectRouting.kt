// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CancellationException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class SourceProjectRouter<P>(
  private val parsePath: (String) -> Path?,
  private val normalizePath: (String) -> String,
  private val resolveManagedPath: (Path) -> String?,
  private val openProjectsProvider: () -> List<P>,
  private val projectIdentityPath: (P) -> String?,
  private val isPathEquivalent: (P, Path) -> Boolean,
  private val openProjectByPath: suspend (Path, OpenProjectTask) -> P?,
) {
  fun findOpenProject(normalizedPath: String): P? {
    val target = resolveTarget(normalizedPath) ?: return null
    return findOpenProject(target)
  }

  suspend fun openOrReuseProject(
    normalizedPath: String,
    options: OpenProjectTask = OpenProjectTask(),
  ): P? {
    val target = resolveTarget(normalizedPath) ?: return null
    return findOpenProject(target) ?: openProjectByPath(target.managedPath, options)
  }

  private fun findOpenProject(target: ResolvedSourceProjectPath): P? {
    val openProjects = openProjectsProvider()
    val directMatch = openProjects.firstOrNull { project ->
      projectIdentityPath(project)?.let(normalizePath) == target.managedNormalizedPath
    }
    if (directMatch != null) {
      return directMatch
    }

    return openProjects.firstOrNull { project ->
      isPathEquivalent(project, target.requestedPath)
    }
  }

  private fun resolveTarget(normalizedPath: String): ResolvedSourceProjectPath? {
    val requestedNormalizedPath = normalizePath(normalizedPath)
    val requestedPath = parsePath(requestedNormalizedPath) ?: return null
    val managedNormalizedPath = resolveManagedPath(requestedPath)
      ?.let(normalizePath)
      ?: requestedNormalizedPath
    val managedPath = parsePath(managedNormalizedPath) ?: requestedPath
    return ResolvedSourceProjectPath(
      requestedPath = requestedPath,
      managedNormalizedPath = managedNormalizedPath,
      managedPath = managedPath,
    )
  }
}

private data class ResolvedSourceProjectPath(
  val requestedPath: Path,
  val managedNormalizedPath: String,
  val managedPath: Path,
)

private val DEFAULT_SOURCE_PROJECT_ROUTER = SourceProjectRouter(
  parsePath = ::parseAgentWorkbenchPathOrNull,
  normalizePath = ::normalizeAgentWorkbenchPath,
  resolveManagedPath = { path -> RecentProjectsManagerBase.getInstanceEx().getProjectPath(path) },
  openProjectsProvider = { ProjectManager.getInstance().openProjects.toList() },
  projectIdentityPath = { project ->
    RecentProjectsManagerBase.getInstanceEx().getProjectPath(project)?.invariantSeparatorsPathString
  },
  isPathEquivalent = { project, path ->
    runCatching { isSameProject(projectFile = path, project = project) }.getOrDefault(false)
  },
  openProjectByPath = { path, options ->
    RecentProjectsManagerBase.getInstanceEx().openProject(path, options)
  },
)

internal fun findOpenSourceProjectByPath(normalizedPath: String): Project? {
  return DEFAULT_SOURCE_PROJECT_ROUTER.findOpenProject(normalizedPath)
}

internal suspend fun openOrReuseSourceProjectByPath(
  normalizedPath: String,
  options: OpenProjectTask = OpenProjectTask(),
): Project? {
  return try {
    DEFAULT_SOURCE_PROJECT_ROUTER.openOrReuseProject(normalizedPath, options)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (t: Throwable) {
    LOG.warn("Failed to open source project at $normalizedPath", t)
    null
  }
}

private val LOG = logger<SourceProjectRouter<*>>()
