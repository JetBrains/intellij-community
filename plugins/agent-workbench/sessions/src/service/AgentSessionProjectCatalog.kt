// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.git.GitWorktreeDiscovery
import com.intellij.agent.workbench.sessions.git.GitWorktreeInfo
import com.intellij.agent.workbench.sessions.git.shortBranchName
import com.intellij.agent.workbench.sessions.git.worktreeDisplayName
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

internal class AgentSessionProjectCatalog {
  suspend fun collectProjects(): List<ProjectEntry> {
    val rawEntries = collectRawProjectEntries()
    if (rawEntries.isEmpty()) return emptyList()

    val repoRootByPath = rawEntries.associate { entry ->
      entry.path to GitWorktreeDiscovery.detectRepoRoot(entry.path)
    }

    data class RepoGroup(
      val repoRoot: String,
      val members: MutableList<IndexedValue<ProjectEntry>>,
    )

    val repoGroups = LinkedHashMap<String, RepoGroup>()
    val standaloneEntries = mutableListOf<IndexedValue<ProjectEntry>>()

    rawEntries.forEachIndexed { index, entry ->
      val repoRoot = repoRootByPath[entry.path]
      if (repoRoot != null) {
        val group = repoGroups.computeIfAbsent(repoRoot) {
          RepoGroup(repoRoot, mutableListOf())
        }
        group.members.add(IndexedValue(index, entry))
      }
      else {
        standaloneEntries.add(IndexedValue(index, entry))
      }
    }

    // Discover all worktrees in parallel across repo roots (main + linked).
    val discoveredByRepoRoot = coroutineScope {
      repoGroups.keys.map { repoRoot ->
        async { repoRoot to GitWorktreeDiscovery.discoverWorktrees(repoRoot) }
      }.awaitAll().toMap()
    }

    val resultEntries = mutableListOf<IndexedValue<ProjectEntry>>()
    for ((repoRoot, group) in repoGroups) {
      val mainRaw = group.members.firstOrNull { it.value.path == repoRoot }
      val worktreeRaws = group.members.filter { it.value.path != repoRoot }
      val firstIndex = group.members.minOf { it.index }

      val discoveredWorktrees = discoveredByRepoRoot[repoRoot] ?: emptyList()
      val worktreeEntries = buildWorktreeEntries(worktreeRaws.map { it.value }, discoveredWorktrees)
      val entry = buildRepoProjectEntry(
        mainRaw = mainRaw?.value,
        repoRoot = repoRoot,
        worktreeEntries = worktreeEntries,
        discoveredWorktrees = discoveredWorktrees,
      ) ?: continue
      resultEntries.add(IndexedValue(firstIndex, entry))
    }

    for (indexed in standaloneEntries) {
      resultEntries.add(indexed)
    }

    return withProjectBuildSystemBadges(resultEntries.sortedBy { it.index }.map { it.value })
  }

  private suspend fun withProjectBuildSystemBadges(entries: List<ProjectEntry>): List<ProjectEntry> = withContext(Dispatchers.IO) {
    val snapshot = currentProjectOpenProcessorSnapshot()
    val entriesWithBadges = ArrayList<ProjectEntry>(entries.size)
    val activePaths = LinkedHashSet<String>(entries.size)

    for (entry in entries) {
      val normalizedPath = normalizeAgentWorkbenchPath(entry.path)
      activePaths.add(normalizedPath)
      val buildSystemBadge = projectBuildSystemBadgeCache.getOrDetect(
        normalizedPath = normalizedPath,
        snapshot = snapshot,
        fileResolver = ::resolveProjectVirtualFile,
        detector = ::detectProjectBuildSystemBadge,
      )
      entriesWithBadges.add(if (buildSystemBadge == null) entry else entry.copy(buildSystemBadge = buildSystemBadge))
    }

    projectBuildSystemBadgeCache.prune(activePaths)
    return@withContext entriesWithBadges
  }
}

internal data class ProjectOpenProcessorSnapshot(
  @JvmField val processors: List<ProjectOpenProcessor>,
)

private fun currentProjectOpenProcessorSnapshot(): ProjectOpenProcessorSnapshot {
  return ProjectOpenProcessor.EXTENSION_POINT_NAME.computeIfAbsent(ProjectOpenProcessorSnapshot::class.java) {
    ProjectOpenProcessorSnapshot(ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.toList())
  }
}

internal class ProjectBuildSystemBadgeCatalogCache {
  private sealed interface CachedBadge {
    fun asBadgeOrNull(): ProjectBuildSystemBadge?

    data class Present(@JvmField val badge: ProjectBuildSystemBadge) : CachedBadge {
      override fun asBadgeOrNull(): ProjectBuildSystemBadge = badge
    }

    data object NoBadge : CachedBadge {
      override fun asBadgeOrNull(): ProjectBuildSystemBadge? = null
    }
  }

  private val projectBuildSystemBadgeByPath = ConcurrentHashMap<String, CachedBadge>()

  @Volatile
  private var cachedProcessorSnapshot: ProjectOpenProcessorSnapshot? = null

  fun getOrDetect(
    normalizedPath: String,
    snapshot: ProjectOpenProcessorSnapshot,
    fileResolver: (String) -> VirtualFile?,
    detector: (VirtualFile?, Iterable<ProjectOpenProcessor>) -> ProjectBuildSystemBadge?,
  ): ProjectBuildSystemBadge? {
    if (cachedProcessorSnapshot !== snapshot) {
      projectBuildSystemBadgeByPath.clear()
      cachedProcessorSnapshot = snapshot
    }

    val cachedBadge = projectBuildSystemBadgeByPath[normalizedPath]
    if (cachedBadge != null) {
      return cachedBadge.asBadgeOrNull()
    }

    val file = fileResolver(normalizedPath) ?: return null
    val detectedBadge = detector(file, snapshot.processors)
    val computedBadge = if (detectedBadge == null) CachedBadge.NoBadge else CachedBadge.Present(detectedBadge)
    val previousBadge = projectBuildSystemBadgeByPath.putIfAbsent(normalizedPath, computedBadge)
    return previousBadge?.asBadgeOrNull() ?: detectedBadge
  }

  fun prune(activePaths: Set<String>) {
    for (cachedPath in projectBuildSystemBadgeByPath.keys) {
      if (cachedPath !in activePaths) {
        projectBuildSystemBadgeByPath.remove(cachedPath)
      }
    }
  }
}

private fun resolveProjectVirtualFile(
  path: String,
  fileSystem: LocalFileSystem = LocalFileSystem.getInstance(),
): VirtualFile? {
  return fileSystem.findFileByPath(path)
}

internal fun detectProjectBuildSystemBadge(
  file: VirtualFile?,
  processors: Iterable<ProjectOpenProcessor> = ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList,
): ProjectBuildSystemBadge? {
  if (file == null || !file.isValid) {
    return null
  }

  var matchedBadge: ProjectBuildSystemBadge? = null
  for (processor in processors) {
    val badge = runCatching {
      if (!processor.canOpenProject(file)) {
        return@runCatching null
      }
      val icon = processor.getIcon(file) ?: return@runCatching null
      ProjectBuildSystemBadge(id = processor.javaClass.name, icon = icon)
    }
      .onFailure { LOG.warn("Failed to resolve project build-system badge for ${file.path} using ${processor.javaClass.name}", it) }
      .getOrNull()
      ?: continue

    if (matchedBadge != null) {
      return null
    }
    matchedBadge = badge
  }
  return matchedBadge
}

internal fun buildRepoProjectEntry(
  mainRaw: ProjectEntry?,
  repoRoot: String,
  worktreeEntries: List<WorktreeEntry>,
  discoveredWorktrees: List<GitWorktreeInfo>,
): ProjectEntry? {
  val mainBranch = shortBranchName(discoveredWorktrees.firstOrNull { it.isMain }?.branch)
  if (worktreeEntries.isEmpty()) {
    return mainRaw?.copy(branch = mainBranch)
  }

  return mainRaw?.copy(worktreeEntries = worktreeEntries, branch = mainBranch)
         ?: ProjectEntry(
           path = repoRoot,
           name = worktreeDisplayName(repoRoot),
           project = null,
           branch = mainBranch,
           worktreeEntries = worktreeEntries,
         )
}

private fun buildWorktreeEntries(
  openRawEntries: List<ProjectEntry>,
  discovered: List<GitWorktreeInfo>,
): List<WorktreeEntry> {
  val openPaths = openRawEntries.mapTo(LinkedHashSet()) { it.path }
  val result = mutableListOf<WorktreeEntry>()

  for (raw in openRawEntries) {
    val gitInfo = discovered.firstOrNull { it.path == raw.path }
    result.add(
      WorktreeEntry(
        path = raw.path,
        name = raw.name,
        branch = shortBranchName(gitInfo?.branch),
        project = raw.project,
      ),
    )
  }

  for (info in discovered) {
    if (info.path !in openPaths && !info.isMain) {
      result.add(
        WorktreeEntry(
          path = info.path,
          name = worktreeDisplayName(info.path),
          branch = shortBranchName(info.branch),
          project = null,
        ),
      )
    }
  }

  return result
}

private fun collectRawProjectEntries(): List<ProjectEntry> {
  val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
                ?: return emptyList()
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProjects = ProjectManager.getInstance().openProjects
  val openCandidates = openProjects.asSequence()
    .filterNot { project -> AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project) }
    .map { project ->
      OpenProjectCandidate(
        project = project,
        normalizedPath = resolveOpenProjectPath(
          managerProjectPath = manager.getProjectPath(project)?.invariantSeparatorsPathString,
          projectBasePath = project.basePath,
        ),
      )
    }
    .toList()
  val consumedOpenProjects = LinkedHashSet<Project>()
  val seen = LinkedHashSet<String>()
  val entries = mutableListOf<ProjectEntry>()
  for (path in manager.getRecentPaths()) {
    val normalized = normalizeAgentWorkbenchPath(path)
    if (normalized == dedicatedProjectPath || AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(normalized)) continue
    if (!seen.add(normalized)) continue
    val openProject = resolveOpenProjectForRecentPath(normalized, openCandidates, consumedOpenProjects)
    if (openProject != null) {
      consumedOpenProjects.add(openProject)
    }
    entries.add(
      ProjectEntry(
        path = normalized,
        name = resolveProjectName(manager, normalized, openProject),
        project = openProject,
      ),
    )
  }
  for (candidate in openCandidates) {
    val project = candidate.project
    if (project in consumedOpenProjects) continue
    val path = candidate.normalizedPath ?: continue
    if (path == dedicatedProjectPath || AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(path)) continue
    if (!seen.add(path)) continue
    entries.add(
      ProjectEntry(
        path = path,
        name = resolveProjectName(manager, path, project),
        project = project,
      ),
    )
  }
  return entries
}

private data class OpenProjectCandidate(
  val project: Project,
  val normalizedPath: String?,
)

private fun resolveOpenProjectForRecentPath(
  normalizedRecentPath: String,
  openCandidates: List<OpenProjectCandidate>,
  consumedOpenProjects: Set<Project>,
): Project? {
  val recentPath = parseAgentWorkbenchPathOrNull(normalizedRecentPath)
  return resolveRecentPathCandidate(
    normalizedRecentPath = normalizedRecentPath,
    candidates = openCandidates,
    consumedCandidates = consumedOpenProjects,
    candidateProject = { it.project },
    candidatePath = { it.normalizedPath },
    isPathEquivalent = { candidate ->
      val path = recentPath ?: return@resolveRecentPathCandidate false
      runCatching { isSameProject(projectFile = path, project = candidate.project) }.getOrDefault(false)
    },
  )?.project
}

internal fun <T, P> resolveRecentPathCandidate(
  normalizedRecentPath: String,
  candidates: List<T>,
  consumedCandidates: Set<P>,
  candidateProject: (T) -> P,
  candidatePath: (T) -> String?,
  isPathEquivalent: (T) -> Boolean,
): T? {
  val directMatch = candidates.firstOrNull { candidate ->
    candidateProject(candidate) !in consumedCandidates &&
    candidatePath(candidate) == normalizedRecentPath
  }
  if (directMatch != null) {
    return directMatch
  }

  return candidates.firstOrNull { candidate ->
    candidateProject(candidate) !in consumedCandidates && isPathEquivalent(candidate)
  }
}

internal fun resolveOpenProjectPath(
  managerProjectPath: String?,
  projectBasePath: String?,
): String? {
  return managerProjectPath?.let(::normalizeAgentWorkbenchPath)
         ?: projectBasePath?.let(::normalizeAgentWorkbenchPath)
}

private fun resolveProjectName(
  manager: RecentProjectsManagerBase,
  path: String,
  project: Project?,
): String {
  val displayName = manager.getDisplayName(path).takeIf { !it.isNullOrBlank() }
  if (displayName != null) return displayName
  val projectName = manager.getProjectName(path)
  if (projectName.isNotBlank()) return projectName
  if (project != null) return project.name
  return resolveProjectNameWithoutManager(path, project)
}

private fun resolveProjectNameWithoutManager(path: String, project: Project?): String {
  if (project != null) return project.name
  val fileName = try {
    Path.of(path).name
  }
  catch (_: InvalidPathException) {
    null
  }
  return fileName ?: FileUtilRt.toSystemDependentName(path)
}
