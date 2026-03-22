// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

fun buildAgentSessionProjectPathCandidates(paths: List<String>): List<AgentPromptProjectPathCandidate> {
  val openProjectsByPath = collectOpenProjectsByPath(RecentProjectsManager.getInstance() as? RecentProjectsManagerBase)
  return buildAgentSessionProjectPathCandidates(paths) { path ->
    resolveAgentSessionProjectDisplayName(path = path, project = openProjectsByPath[path])
  }
}

internal fun buildAgentSessionProjectPathCandidates(
  paths: List<String>,
  resolveDisplayName: (String) -> @NlsSafe String,
): List<AgentPromptProjectPathCandidate> {
  val normalizedPaths = paths.asSequence()
    .map(::normalizeAgentWorkbenchPath)
    .filter { path -> path.isNotBlank() && !AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(path) }
    .distinct()
    .toList()
  if (normalizedPaths.isEmpty()) {
    return emptyList()
  }

  val displayNamesByPath = normalizedPaths.associateWith(resolveDisplayName)
  val duplicateDisplayNames = displayNamesByPath.values.groupingBy { it }.eachCount()
    .filterValues { count -> count > 1 }
    .keys
  return normalizedPaths.map { path ->
    val displayName = displayNamesByPath.getValue(path)
    AgentPromptProjectPathCandidate(
      path = path,
      displayName = if (displayName in duplicateDisplayNames) path else displayName,
    )
  }
}

fun resolveAgentSessionProjectDisplayName(path: String, project: Project?): @NlsSafe String {
  val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
  return resolveAgentSessionProjectDisplayName(
    path = path,
    project = project,
    recentProjectDisplayName = manager?.let { recentProjectsManager -> recentProjectsManager::getDisplayName } ?: { null },
    recentProjectName = manager?.let { recentProjectsManager -> recentProjectsManager::getProjectName } ?: { "" },
  )
}

internal fun resolveAgentSessionProjectDisplayName(
  path: String,
  project: Project?,
  recentProjectDisplayName: (String) -> String?,
  recentProjectName: (String) -> String,
): @NlsSafe String {
  val displayName = recentProjectDisplayName(path).takeIf { !it.isNullOrBlank() }
  if (displayName != null) return displayName
  val projectName = recentProjectName(path)
  if (projectName.isNotBlank()) return projectName
  if (project != null) return project.name
  return resolveAgentSessionProjectDisplayNameWithoutManager(path, project)
}

internal fun resolveAgentSessionProjectDisplayNameWithoutManager(path: String, project: Project?): @NlsSafe String {
  if (project != null) return project.name
  val fileName = try {
    Path.of(path).name
  }
  catch (_: InvalidPathException) {
    null
  }
  return fileName ?: FileUtilRt.toSystemDependentName(path)
}

private fun collectOpenProjectsByPath(manager: RecentProjectsManagerBase?): Map<String, Project> {
  return ProjectManager.getInstance().openProjects.asSequence()
    .filterNot(AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject)
    .mapNotNull { project ->
      val path = resolveOpenProjectPath(
        managerProjectPath = manager?.getProjectPath(project)?.invariantSeparatorsPathString,
        projectBasePath = project.basePath,
      ) ?: return@mapNotNull null
      path to project
    }
    .toMap()
}
