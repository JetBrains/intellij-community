// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.git.GitWorktreeDiscovery
import com.intellij.agent.workbench.sessions.git.GitWorktreeInfo
import com.intellij.agent.workbench.sessions.git.shortBranchName
import com.intellij.agent.workbench.sessions.git.worktreeDisplayName
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchProjectDirectory
import com.intellij.agent.workbench.sessions.model.ProjectBuildSystemBadge
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<AgentSessionProjectCatalog>()

internal class AgentSessionProjectCatalog {
  private val projectBuildSystemBadgeCache = ProjectBuildSystemBadgeCatalogCache()
  private val recentProjectDirectoryStore: AgentSessionRecentProjectDirectoryStore = service<AgentSessionRecentProjectDirectoryService>()

  suspend fun collectProjects(): List<ProjectEntry> {
    val rawEntriesSnapshot = collectRawProjectEntries(recentProjectDirectoryStore = recentProjectDirectoryStore)
    rememberRecentProjectDirectories(
      recentPaths = rawEntriesSnapshot.recentPaths,
      rawEntries = rawEntriesSnapshot.entries,
      authoritativeProjectDirectoriesByRecentPath = rawEntriesSnapshot.authoritativeProjectDirectoriesByRecentPath,
      recentProjectDirectoryStore = recentProjectDirectoryStore,
    )
    val rawEntries = rawEntriesSnapshot.entries
    if (rawEntries.isEmpty()) return emptyList()

    val repoRootByPath = rawEntries.associate { entry ->
      entry.path to GitWorktreeDiscovery.detectRepoRoot(entry.projectDirectory ?: entry.path)
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
      val mainRaw = selectRepoMainEntry(group.members, repoRoot)
      val worktreeRaws = group.members.filter { indexedEntry -> indexedEntry != mainRaw }
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

internal fun rememberRecentProjectDirectories(
  recentPaths: Set<String>,
  rawEntries: List<ProjectEntry>,
  authoritativeProjectDirectoriesByRecentPath: Map<String, String>,
  recentProjectDirectoryStore: AgentSessionRecentProjectDirectoryStore,
) {
  val openProjectDirectoriesByPath = LinkedHashMap<String, String>()
  for ((path, projectDirectory) in authoritativeProjectDirectoriesByRecentPath) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedProjectDirectory = normalizeAgentWorkbenchPath(projectDirectory)
    if (normalizedPath in recentPaths && normalizedProjectDirectory.isNotBlank()) {
      openProjectDirectoriesByPath[normalizedPath] = normalizedProjectDirectory
    }
  }
  for (entry in rawEntries) {
    if (entry.project != null && entry.path in recentPaths) {
      entry.projectDirectory?.takeIf { it.isNotBlank() }?.let { projectDirectory ->
        openProjectDirectoriesByPath[entry.path] = normalizeAgentWorkbenchPath(projectDirectory)
      }
    }
  }
  recentProjectDirectoryStore.syncRecentPaths(
    recentPaths = recentPaths,
    authoritativeProjectDirectoriesByPath = openProjectDirectoriesByPath,
  )
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
                  .onFailure {
                    LOG.warn("Failed to resolve project build-system badge for ${file.path} using ${processor.javaClass.name}",
                             it)
                  }
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
           projectDirectory = repoRoot,
           name = worktreeDisplayName(repoRoot),
           project = null,
           branch = mainBranch,
           worktreeEntries = worktreeEntries,
         )
}

internal fun buildWorktreeEntries(
  openRawEntries: List<ProjectEntry>,
  discovered: List<GitWorktreeInfo>,
): List<WorktreeEntry> {
  val openProjectDirectories = openRawEntries.mapTo(LinkedHashSet()) { entry -> normalizeRepoProjectDirectory(entry) }
  val mainWorktreeDirectories = discovered.asSequence()
    .filter { info -> info.isMain }
    .mapTo(LinkedHashSet()) { info -> normalizeAgentWorkbenchPath(info.path) }
  val result = mutableListOf<WorktreeEntry>()

  for (raw in openRawEntries) {
    val rawProjectDirectory = normalizeRepoProjectDirectory(raw)
    val rawPath = normalizeAgentWorkbenchPath(raw.path)
    if (rawProjectDirectory in mainWorktreeDirectories && rawPath == rawProjectDirectory) {
      continue
    }
    val gitInfo = discovered.firstOrNull { info -> normalizeAgentWorkbenchPath(info.path) == rawProjectDirectory }
    result.add(
      WorktreeEntry(
        path = raw.path,
        projectDirectory = raw.projectDirectory,
        name = raw.name,
        branch = shortBranchName(gitInfo?.branch),
        project = raw.project,
      ),
    )
  }

  for (info in discovered) {
    if (normalizeAgentWorkbenchPath(info.path) !in openProjectDirectories && !info.isMain) {
      result.add(
        WorktreeEntry(
          path = info.path,
          projectDirectory = info.path,
          name = worktreeDisplayName(info.path),
          branch = shortBranchName(info.branch),
          project = null,
        ),
      )
    }
  }

  return result
}

internal data class RawProjectEntriesSnapshot(
  val entries: List<ProjectEntry>,
  val recentPaths: Set<String>,
  val authoritativeProjectDirectoriesByRecentPath: Map<String, String> = emptyMap(),
)

private fun collectRawProjectEntries(
  recentProjectDirectoryStore: AgentSessionRecentProjectDirectoryStore,
): RawProjectEntriesSnapshot {
  val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
                ?: return RawProjectEntriesSnapshot(entries = emptyList(), recentPaths = emptySet())
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProjects = ProjectManager.getInstance().openProjects
  val openCandidates = openProjects.asSequence()
    .filterNot { project -> AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project) }
    .map { project ->
      val normalizedPath = resolveOpenProjectPath(
        managerProjectPath = manager.getProjectPath(project),
        projectBasePath = project.basePath,
      )
      OpenProjectCandidate(
        project = project,
        normalizedPath = normalizedPath,
        projectDirectory = resolveAgentWorkbenchProjectDirectory(
          identityPath = normalizedPath,
          projectBasePath = project.basePath,
        ),
      )
    }
    .toList()
  return buildRawProjectEntries(
    recentPaths = manager.getRecentPaths(),
    openCandidates = openCandidates,
    dedicatedProjectPath = dedicatedProjectPath,
    recentProjectDirectoryStore = recentProjectDirectoryStore,
    resolveProjectName = { path, project -> resolveProjectName(manager, path, project) },
  )
}

internal fun buildRawProjectEntries(
  recentPaths: Iterable<String>,
  openCandidates: List<OpenProjectCandidate>,
  dedicatedProjectPath: String,
  recentProjectDirectoryStore: AgentSessionRecentProjectDirectoryStore,
  resolveProjectName: (String, Project?) -> String,
  isDedicatedProjectPath: (String) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProjectPath,
  isPathEquivalentToProject: (Project, Path) -> Boolean = { project, path ->
    runCatching { isSameProject(projectFile = path, project = project) }.getOrDefault(false)
  },
): RawProjectEntriesSnapshot {
  val consumedOpenProjects = LinkedHashSet<Project>()
  val normalizedRecentPaths = LinkedHashSet<String>()
  val seen = LinkedHashSet<String>()
  val entries = mutableListOf<ProjectEntry>()
  val authoritativeProjectDirectoriesByRecentPath = LinkedHashMap<String, String>()
  for (path in recentPaths) {
    val normalized = normalizeAgentWorkbenchPath(path)
    if (normalized == dedicatedProjectPath || isDedicatedProjectPath(normalized)) continue
    normalizedRecentPaths.add(normalized)
    val openProjectCandidate = resolveOpenProjectForRecentPath(
      normalizedRecentPath = normalized,
      openCandidates = openCandidates,
      consumedOpenProjects = emptySet(),
      isPathEquivalentToProject = isPathEquivalentToProject,
    )
    if (openProjectCandidate != null) {
      openProjectCandidate.projectDirectory?.takeIf { it.isNotBlank() }?.let { projectDirectory ->
        authoritativeProjectDirectoriesByRecentPath[normalized] = normalizeAgentWorkbenchPath(projectDirectory)
      }
      if (!consumedOpenProjects.add(openProjectCandidate.project)) {
        continue
      }
      val openProjectPath = openProjectCandidate.normalizedPath ?: normalized
      if (openProjectPath == dedicatedProjectPath || isDedicatedProjectPath(openProjectPath)) continue
      if (!seen.add(openProjectPath)) continue
      entries.add(
        ProjectEntry(
          path = openProjectPath,
          projectDirectory = resolveRecentProjectDirectory(
            identityPath = openProjectPath,
            openProjectDirectory = openProjectCandidate.projectDirectory,
            rememberedProjectDirectory = recentProjectDirectoryStore.getProjectDirectory(openProjectPath),
          ),
          name = resolveProjectName(openProjectPath, openProjectCandidate.project),
          project = openProjectCandidate.project,
        ),
      )
      continue
    }
    if (!seen.add(normalized)) continue
    entries.add(
      ProjectEntry(
        path = normalized,
        projectDirectory = resolveRecentProjectDirectory(
          identityPath = normalized,
          rememberedProjectDirectory = recentProjectDirectoryStore.getProjectDirectory(normalized),
        ),
        name = resolveProjectName(normalized, null),
        project = null,
      ),
    )
  }
  for (candidate in openCandidates) {
    val project = candidate.project
    if (project in consumedOpenProjects) continue
    val path = candidate.normalizedPath ?: continue
    if (path == dedicatedProjectPath || isDedicatedProjectPath(path)) continue
    if (!seen.add(path)) continue
    entries.add(
      ProjectEntry(
        path = path,
        projectDirectory = candidate.projectDirectory,
        name = resolveProjectName(path, project),
        project = project,
      ),
    )
  }
  return RawProjectEntriesSnapshot(
    entries = entries,
    recentPaths = normalizedRecentPaths,
    authoritativeProjectDirectoriesByRecentPath = authoritativeProjectDirectoriesByRecentPath,
  )
}

internal data class OpenProjectCandidate(
  val project: Project,
  val normalizedPath: String?,
  val projectDirectory: String?,
)

private fun resolveOpenProjectForRecentPath(
  normalizedRecentPath: String,
  openCandidates: List<OpenProjectCandidate>,
  consumedOpenProjects: Set<Project>,
  isPathEquivalentToProject: (Project, Path) -> Boolean = { project, path ->
    runCatching { isSameProject(projectFile = path, project = project) }.getOrDefault(false)
  },
): OpenProjectCandidate? {
  val recentPath = parseAgentWorkbenchPathOrNull(normalizedRecentPath)
  return resolveRecentPathCandidate(
    normalizedRecentPath = normalizedRecentPath,
    candidates = openCandidates,
    consumedCandidates = consumedOpenProjects,
    candidateProject = { it.project },
    candidatePath = { it.normalizedPath },
    isPathEquivalent = { candidate ->
      val path = recentPath ?: return@resolveRecentPathCandidate false
      isPathEquivalentToProject(candidate.project, path)
    },
  )
}

internal fun resolveRecentProjectDirectory(
  identityPath: String,
  openProjectDirectory: String? = null,
  rememberedProjectDirectory: String? = null,
): String? {
  openProjectDirectory?.takeIf { it.isNotBlank() }?.let(::normalizeAgentWorkbenchPath)?.let { directory ->
    return directory
  }
  rememberedProjectDirectory?.takeIf { it.isNotBlank() }?.let(::normalizeAgentWorkbenchPath)?.let { directory ->
    return directory
  }
  return resolveAgentWorkbenchProjectDirectory(identityPath = identityPath)
}

internal fun selectRepoMainEntry(
  members: List<IndexedValue<ProjectEntry>>,
  repoRoot: String,
): IndexedValue<ProjectEntry>? {
  val normalizedRepoRoot = normalizeAgentWorkbenchPath(repoRoot)
  return members.firstOrNull { indexedEntry ->
    val entry = indexedEntry.value
    normalizeRepoProjectDirectory(entry) == normalizedRepoRoot && normalizeAgentWorkbenchPath(entry.path) != normalizedRepoRoot
  } ?: members.firstOrNull { indexedEntry ->
    normalizeAgentWorkbenchPath(indexedEntry.value.path) == normalizedRepoRoot
  } ?: members.firstOrNull { indexedEntry ->
    normalizeRepoProjectDirectory(indexedEntry.value) == normalizedRepoRoot
  }
}

private fun normalizeRepoProjectDirectory(entry: ProjectEntry): String {
  return entry.projectDirectory
           ?.takeIf { it.isNotBlank() }
           ?.let(::normalizeAgentWorkbenchPath)
         ?: normalizeAgentWorkbenchPath(entry.path)
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
  managerProjectPath: Path?,
  projectBasePath: String?,
): String? {
  return managerProjectPath?.invariantSeparatorsPathString
         ?: projectBasePath?.let(::normalizeAgentWorkbenchPath)
}

fun resolveOpenProjectIdentityPath(
  project: Project,
  manager: RecentProjectsManagerBase? = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase,
): String? {
  return resolveOpenProjectPath(
    managerProjectPath = manager?.getProjectPath(project),
    projectBasePath = project.basePath,
  )
}

private fun resolveProjectName(
  manager: RecentProjectsManagerBase,
  path: String,
  project: Project?,
): String {
  return resolveAgentSessionProjectDisplayName(
    path = path,
    project = project,
    recentProjectDisplayName = manager::getDisplayName,
    recentProjectName = manager::getProjectName,
  )
}
