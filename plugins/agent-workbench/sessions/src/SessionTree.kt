package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.openapi.util.NlsSafe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private val THREAD_TITLE_WHITESPACE = Regex("\\s+")

internal data class SessionTreeModel(
  val rootIds: List<SessionTreeId>,
  val entriesById: Map<SessionTreeId, SessionTreeModelEntry>,
  val autoOpenProjects: List<SessionTreeId.Project>,
) {
  companion object {
    val EMPTY: SessionTreeModel = SessionTreeModel(
      rootIds = emptyList(),
      entriesById = emptyMap(),
      autoOpenProjects = emptyList(),
    )
  }
}

internal data class SessionTreeModelEntry(
  val id: SessionTreeId,
  val parentId: SessionTreeId?,
  val node: SessionTreeNode,
  val childIds: List<SessionTreeId> = emptyList(),
)

internal data class SessionTreeModelDiff(
  val rootChanged: Boolean,
  val structureChangedIds: Set<SessionTreeId>,
  val contentChangedIds: Set<SessionTreeId>,
)

internal data class VisibleProjectsResult(
  val visibleProjects: List<AgentProjectSessions>,
  val hiddenClosedProjectCount: Int,
)

internal fun buildSessionTreeModel(
  projects: List<AgentProjectSessions>,
  visibleClosedProjectCount: Int,
  visibleThreadCounts: Map<String, Int>,
  treeUiState: SessionsTreeUiState,
): SessionTreeModel {
  val visibleProjectsResult = computeVisibleProjects(projects, visibleClosedProjectCount)
  val modelBuilder = SessionTreeModelBuilder(visibleThreadCounts)
  val baseModel = modelBuilder.build(visibleProjectsResult)
  val autoOpenProjects = visibleProjectsResult.visibleProjects
    .filter {
      it.isOpen ||
      it.errorMessage != null ||
      it.providerWarnings.isNotEmpty() ||
      it.worktrees.any { wt -> wt.isOpen }
    }
    .filterNot { treeUiState.isProjectCollapsed(it.path) }
    .map { SessionTreeId.Project(it.path) }
  return baseModel.copy(autoOpenProjects = autoOpenProjects)
}

internal fun diffSessionTreeModels(
  oldModel: SessionTreeModel,
  newModel: SessionTreeModel,
): SessionTreeModelDiff {
  val rootChanged = oldModel.rootIds != newModel.rootIds
  val structureChangedIds = LinkedHashSet<SessionTreeId>()
  val contentChangedIds = LinkedHashSet<SessionTreeId>()
  oldModel.entriesById.forEach { (id, oldEntry) ->
    val newEntry = newModel.entriesById[id] ?: return@forEach
    if (oldEntry.childIds != newEntry.childIds) {
      structureChangedIds += id
    }
    if (sessionTreeNodePresentation(oldEntry.node) != sessionTreeNodePresentation(newEntry.node)) {
      contentChangedIds += id
    }
  }
  contentChangedIds.removeAll(structureChangedIds)
  return SessionTreeModelDiff(
    rootChanged = rootChanged,
    structureChangedIds = structureChangedIds,
    contentChangedIds = contentChangedIds,
  )
}

private data class ProjectTreeRowPresentation(
  val name: @NlsSafe String,
  val isOpen: Boolean,
  val hasOpenWorktree: Boolean,
  val hasWorktrees: Boolean,
  val branch: String?,
  val isLoading: Boolean,
)

private data class WorktreeTreeRowPresentation(
  val name: @NlsSafe String,
  val branch: String?,
  val isLoading: Boolean,
)

private data class ThreadTreeRowPresentation(
  val thread: AgentSessionThread,
  val parentWorktreeBranch: String?,
)

private data class MoreThreadsTreeRowPresentation(
  val hiddenCount: Int?,
)

internal fun sessionTreeNodePresentation(node: SessionTreeNode): Any {
  return when (node) {
    is SessionTreeNode.Project -> {
      val hasWorktrees = node.project.worktrees.isNotEmpty()
      ProjectTreeRowPresentation(
        name = node.project.name,
        isOpen = node.project.isOpen,
        hasOpenWorktree = node.project.worktrees.any { it.isOpen },
        hasWorktrees = hasWorktrees,
        branch = if (hasWorktrees) node.project.branch else null,
        isLoading = node.project.isLoading,
      )
    }

    is SessionTreeNode.Worktree -> WorktreeTreeRowPresentation(
      name = node.worktree.name,
      branch = node.worktree.branch,
      isLoading = node.worktree.isLoading,
    )

    is SessionTreeNode.Thread -> ThreadTreeRowPresentation(
      thread = node.thread,
      parentWorktreeBranch = node.parentWorktreeBranch,
    )

    is SessionTreeNode.SubAgent -> node.subAgent
    is SessionTreeNode.Warning -> node.message
    is SessionTreeNode.Error -> node.message
    is SessionTreeNode.Empty -> node.message
    is SessionTreeNode.MoreProjects -> node.hiddenCount
    is SessionTreeNode.MoreThreads -> MoreThreadsTreeRowPresentation(node.hiddenCount)
  }
}

internal fun computeVisibleProjects(
  projects: List<AgentProjectSessions>,
  visibleClosedProjectCount: Int,
): VisibleProjectsResult {
  var remainingClosedCount = visibleClosedProjectCount.coerceAtLeast(0)
  var hiddenClosedProjectCount = 0
  val visibleProjects = ArrayList<AgentProjectSessions>(projects.size)
  for (project in projects) {
    if (project.isOpen || project.worktrees.any { worktree -> worktree.isOpen }) {
      visibleProjects.add(project)
      continue
    }
    if (remainingClosedCount > 0) {
      visibleProjects.add(project)
      remainingClosedCount--
    }
    else {
      hiddenClosedProjectCount++
    }
  }
  return VisibleProjectsResult(
    visibleProjects = visibleProjects,
    hiddenClosedProjectCount = hiddenClosedProjectCount,
  )
}

private class SessionTreeModelBuilder(
  private val visibleThreadCounts: Map<String, Int>,
) {
  private val entriesById = LinkedHashMap<SessionTreeId, SessionTreeModelEntry>()

  fun build(visibleProjectsResult: VisibleProjectsResult): SessionTreeModel {
    val rootIds = mutableListOf<SessionTreeId>()
    visibleProjectsResult.visibleProjects.forEach { project ->
      rootIds += buildProjectEntry(project = project)
    }
    if (visibleProjectsResult.hiddenClosedProjectCount > 0) {
      rootIds += addEntry(
        id = SessionTreeId.MoreProjects,
        parentId = null,
        node = SessionTreeNode.MoreProjects(visibleProjectsResult.hiddenClosedProjectCount),
      )
    }
    return SessionTreeModel(
      rootIds = rootIds,
      entriesById = entriesById,
      autoOpenProjects = emptyList(),
    )
  }

  private fun buildProjectEntry(project: AgentProjectSessions): SessionTreeId.Project {
    val projectId = SessionTreeId.Project(project.path)
    val childIds = mutableListOf<SessionTreeId>()
    val hasVisibleWorktrees = project.worktrees.any {
      it.threads.isNotEmpty() || it.isLoading || it.errorMessage != null || it.providerWarnings.isNotEmpty()
    }
    val errorMessage = project.errorMessage
    if (errorMessage != null) {
      childIds += addEntry(
        id = SessionTreeId.Error(project.path),
        parentId = projectId,
        node = SessionTreeNode.Error(project, errorMessage),
      )
    }
    else if (project.hasLoaded && project.threads.isEmpty() && !hasVisibleWorktrees && project.providerWarnings.isEmpty()) {
      childIds += addEntry(
        id = SessionTreeId.Empty(project.path),
        parentId = projectId,
        node = SessionTreeNode.Empty(project, AgentSessionsBundle.message("toolwindow.empty.project")),
      )
    }
    else {
      childIds += buildProviderWarningEntries(parentId = projectId, warnings = project.providerWarnings) { provider ->
        SessionTreeId.Warning(project.path, provider)
      }
      val visibleWorktrees = project.worktrees.filter {
        it.threads.isNotEmpty() || it.isLoading || it.errorMessage != null || it.providerWarnings.isNotEmpty()
      }
      visibleWorktrees.forEach { worktree ->
        childIds += buildWorktreeEntry(parentId = projectId, project = project, worktree = worktree)
      }
      val visibleCount = visibleThreadCounts[project.path] ?: DEFAULT_VISIBLE_THREAD_COUNT
      childIds += buildThreadEntries(
        parentId = projectId,
        project = project,
        threads = project.threads,
        maxVisible = visibleCount,
        parentWorktreeBranch = null,
        worktreePath = null,
      ) { provider, threadId ->
        SessionTreeId.Thread(project.path, provider, threadId)
      }
      if (project.threads.size > visibleCount) {
        childIds += addEntry(
          id = SessionTreeId.MoreThreads(project.path),
          parentId = projectId,
          node = SessionTreeNode.MoreThreads(
            project = project,
            hiddenCount = if (project.hasUnknownThreadCount) null else project.threads.size - visibleCount,
          ),
        )
      }
    }
    addEntry(
      id = projectId,
      parentId = null,
      node = SessionTreeNode.Project(project),
      childIds = childIds,
    )
    return projectId
  }

  private fun buildWorktreeEntry(
    parentId: SessionTreeId,
    project: AgentProjectSessions,
    worktree: AgentWorktree,
  ): SessionTreeId.Worktree {
    val childIds = mutableListOf<SessionTreeId>()
    val worktreeId = SessionTreeId.Worktree(project.path, worktree.path)
    val errorMessage = worktree.errorMessage
    if (errorMessage != null) {
      childIds += addEntry(
        id = SessionTreeId.WorktreeError(project.path, worktree.path),
        parentId = worktreeId,
        node = SessionTreeNode.Error(project, errorMessage),
      )
    }
    else {
      childIds += buildProviderWarningEntries(parentId = worktreeId, warnings = worktree.providerWarnings) { provider ->
        SessionTreeId.WorktreeWarning(project.path, worktree.path, provider)
      }
      val visibleCount = visibleThreadCounts[worktree.path] ?: DEFAULT_VISIBLE_THREAD_COUNT
      childIds += buildThreadEntries(
        parentId = worktreeId,
        project = project,
        threads = worktree.threads,
        maxVisible = visibleCount,
        parentWorktreeBranch = worktree.branch,
        worktreePath = worktree.path,
      ) { provider, threadId ->
        SessionTreeId.WorktreeThread(project.path, worktree.path, provider, threadId)
      }
      if (worktree.threads.size > visibleCount) {
        childIds += addEntry(
          id = SessionTreeId.WorktreeMoreThreads(project.path, worktree.path),
          parentId = worktreeId,
          node = SessionTreeNode.MoreThreads(
            project = project,
            hiddenCount = if (worktree.hasUnknownThreadCount) null else worktree.threads.size - visibleCount,
          ),
        )
      }
    }
    addEntry(
      id = worktreeId,
      parentId = parentId,
      node = SessionTreeNode.Worktree(project, worktree),
      childIds = childIds,
    )
    return worktreeId
  }

  private fun buildProviderWarningEntries(
    parentId: SessionTreeId,
    warnings: List<AgentSessionProviderWarning>,
    idFactory: (AgentSessionProvider) -> SessionTreeId,
  ): List<SessionTreeId> {
    return warnings.map { warning ->
      addEntry(
        id = idFactory(warning.provider),
        parentId = parentId,
        node = SessionTreeNode.Warning(warning.message),
      )
    }
  }

  private fun buildThreadEntries(
    parentId: SessionTreeId,
    project: AgentProjectSessions,
    threads: List<AgentSessionThread>,
    maxVisible: Int,
    parentWorktreeBranch: String?,
    worktreePath: String?,
    threadIdFactory: (AgentSessionProvider, String) -> SessionTreeId,
  ): List<SessionTreeId> {
    return threads.take(maxVisible).map { thread ->
      val id = threadIdFactory(thread.provider, thread.id)
      val node = SessionTreeNode.Thread(project, thread, parentWorktreeBranch)
      if (thread.subAgents.isEmpty()) {
        addEntry(id = id, parentId = parentId, node = node)
      }
      else {
        val childIds = thread.subAgents.map { subAgent ->
          val subAgentId = if (worktreePath != null) {
            SessionTreeId.WorktreeSubAgent(project.path, worktreePath, thread.provider, thread.id, subAgent.id)
          }
          else {
            SessionTreeId.SubAgent(project.path, thread.provider, thread.id, subAgent.id)
          }
          addEntry(
            id = subAgentId,
            parentId = id,
            node = SessionTreeNode.SubAgent(project, thread, subAgent),
          )
        }
        addEntry(id = id, parentId = parentId, node = node, childIds = childIds)
      }
    }
  }

  private fun addEntry(
    id: SessionTreeId,
    parentId: SessionTreeId?,
    node: SessionTreeNode,
    childIds: List<SessionTreeId> = emptyList(),
  ): SessionTreeId {
    entriesById[id] = SessionTreeModelEntry(
      id = id,
      parentId = parentId,
      node = node,
      childIds = childIds,
    )
    return id
  }
}

internal fun shouldHandleSingleClick(node: SessionTreeNode): Boolean {
  return node is SessionTreeNode.MoreProjects || node is SessionTreeNode.MoreThreads
}

internal fun shouldOpenOnActivation(node: SessionTreeNode): Boolean {
  return when (node) {
    is SessionTreeNode.Project,
    is SessionTreeNode.Worktree,
    is SessionTreeNode.SubAgent -> true

    is SessionTreeNode.Thread -> !isAgentSessionNewSessionId(node.thread.id)

    is SessionTreeNode.Warning,
    is SessionTreeNode.Error,
    is SessionTreeNode.Empty,
    is SessionTreeNode.MoreProjects,
    is SessionTreeNode.MoreThreads -> false
  }
}

internal fun shouldRetargetSelectionForContextMenu(isClickedPathSelected: Boolean): Boolean {
  return !isClickedPathSelected
}

internal data class NewSessionRowActions(
  val path: String,
  val quickProvider: AgentSessionProvider?,
)

internal fun resolveNewSessionRowActions(
  node: SessionTreeNode,
  lastUsedProvider: AgentSessionProvider?,
): NewSessionRowActions? {
  val path = when (node) {
    is SessionTreeNode.Project -> node.project.path

    is SessionTreeNode.Worktree -> node.worktree.path

    is SessionTreeNode.Thread,
    is SessionTreeNode.SubAgent,
    is SessionTreeNode.Warning,
    is SessionTreeNode.Error,
    is SessionTreeNode.Empty,
    is SessionTreeNode.MoreProjects,
    is SessionTreeNode.MoreThreads -> return null
  }
  return NewSessionRowActions(
    path = path,
    quickProvider = resolveQuickCreateProvider(lastUsedProvider),
  )
}

internal fun resolveQuickCreateProvider(lastUsedProvider: AgentSessionProvider?): AgentSessionProvider? {
  val provider = lastUsedProvider ?: return null
  val bridge = AgentSessionProviderBridges.find(provider) ?: return null
  if (AgentSessionLaunchMode.STANDARD !in bridge.supportedLaunchModes) return null
  return provider
}

internal fun pathForMoreThreadsNode(id: SessionTreeId): String? {
  return when (id) {
    is SessionTreeId.MoreThreads -> id.projectPath
    is SessionTreeId.WorktreeMoreThreads -> id.worktreePath
    else -> null
  }
}

internal fun pathForThreadNode(id: SessionTreeId, fallbackProjectPath: String): String {
  return when (id) {
    is SessionTreeId.WorktreeThread -> id.worktreePath
    is SessionTreeId.WorktreeSubAgent -> id.worktreePath
    else -> fallbackProjectPath
  }
}

internal fun archiveTargetFromThreadNode(
  id: SessionTreeId,
  threadNode: SessionTreeNode.Thread,
): ArchiveThreadTarget {
  val path = when (id) {
    is SessionTreeId.WorktreeThread -> id.worktreePath
    else -> threadNode.project.path
  }
  return ArchiveThreadTarget(
    path = normalizeAgentWorkbenchPath(path),
    provider = threadNode.thread.provider,
    threadId = threadNode.thread.id,
  )
}

internal sealed interface SessionTreeNode {
  data class Project(val project: AgentProjectSessions) : SessionTreeNode
  data class Thread(val project: AgentProjectSessions, val thread: AgentSessionThread, val parentWorktreeBranch: String? = null) : SessionTreeNode
  data class SubAgent(
    val project: AgentProjectSessions,
    val thread: AgentSessionThread,
    val subAgent: AgentSubAgent,
  ) : SessionTreeNode
  data class Warning(val message: @NlsSafe String) : SessionTreeNode
  data class Error(val project: AgentProjectSessions, val message: @NlsSafe String) : SessionTreeNode
  data class Empty(val project: AgentProjectSessions, val message: @NlsSafe String) : SessionTreeNode
  data class MoreProjects(val hiddenCount: Int) : SessionTreeNode
  data class MoreThreads(val project: AgentProjectSessions, val hiddenCount: Int?) : SessionTreeNode
  data class Worktree(val project: AgentProjectSessions, val worktree: AgentWorktree) : SessionTreeNode
}

internal sealed interface SessionTreeId {
  data class Project(val path: String) : SessionTreeId
  data class Thread(val projectPath: String, val provider: AgentSessionProvider, val threadId: String) : SessionTreeId
  data class SubAgent(
    val projectPath: String,
    val provider: AgentSessionProvider,
    val threadId: String,
    val subAgentId: String,
  ) : SessionTreeId
  data class Warning(val projectPath: String, val provider: AgentSessionProvider) : SessionTreeId
  data class Error(val projectPath: String) : SessionTreeId
  data class Empty(val projectPath: String) : SessionTreeId
  data object MoreProjects : SessionTreeId
  data class MoreThreads(val projectPath: String) : SessionTreeId
  data class Worktree(val projectPath: String, val worktreePath: String) : SessionTreeId
  data class WorktreeThread(
    val projectPath: String,
    val worktreePath: String,
    val provider: AgentSessionProvider,
    val threadId: String,
  ) : SessionTreeId
  data class WorktreeSubAgent(
    val projectPath: String,
    val worktreePath: String,
    val provider: AgentSessionProvider,
    val threadId: String,
    val subAgentId: String,
  ) : SessionTreeId
  data class WorktreeWarning(
    val projectPath: String,
    val worktreePath: String,
    val provider: AgentSessionProvider,
  ) : SessionTreeId
  data class WorktreeMoreThreads(val projectPath: String, val worktreePath: String) : SessionTreeId
  data class WorktreeError(val projectPath: String, val worktreePath: String) : SessionTreeId
}

internal fun resolveSelectedSessionTreeId(
  projects: List<AgentProjectSessions>,
  selection: AgentChatTabSelection?,
): SessionTreeId? {
  if (selection == null) return null
  val identity = parseAgentSessionIdentity(selection.threadIdentity) ?: return null
  val normalizedPath = normalizeAgentWorkbenchPath(selection.projectPath)

  val worktreeMatch = projects.firstNotNullOfOrNull { project ->
    project.worktrees.firstOrNull { normalizeAgentWorkbenchPath(it.path) == normalizedPath }?.let { worktree -> project to worktree }
  }
  if (worktreeMatch != null) {
    val (project, worktree) = worktreeMatch
    return resolveWorktreeSelection(
      project = project,
      worktree = worktree,
      provider = identity.provider,
      threadId = identity.sessionId,
      subAgentId = selection.subAgentId,
    )
  }

  val project = projects.firstOrNull { normalizeAgentWorkbenchPath(it.path) == normalizedPath } ?: return null
  return resolveProjectSelection(
    project = project,
    provider = identity.provider,
    threadId = identity.sessionId,
    subAgentId = selection.subAgentId,
  )
}

private fun resolveProjectSelection(
  project: AgentProjectSessions,
  provider: AgentSessionProvider,
  threadId: String,
  subAgentId: String?,
): SessionTreeId? {
  val thread = project.threads.firstOrNull { it.provider == provider && it.id == threadId } ?: return null
  if (subAgentId != null && thread.subAgents.any { it.id == subAgentId }) {
    return SessionTreeId.SubAgent(project.path, provider, threadId, subAgentId)
  }
  return SessionTreeId.Thread(project.path, provider, threadId)
}

private fun resolveWorktreeSelection(
  project: AgentProjectSessions,
  worktree: AgentWorktree,
  provider: AgentSessionProvider,
  threadId: String,
  subAgentId: String?,
): SessionTreeId? {
  val thread = worktree.threads.firstOrNull { it.provider == provider && it.id == threadId } ?: return null
  if (subAgentId != null && thread.subAgents.any { it.id == subAgentId }) {
    return SessionTreeId.WorktreeSubAgent(project.path, worktree.path, provider, threadId, subAgentId)
  }
  return SessionTreeId.WorktreeThread(project.path, worktree.path, provider, threadId)
}

internal fun parentNodesForSelection(selectedTreeId: SessionTreeId): List<SessionTreeId> {
  return when (selectedTreeId) {
    is SessionTreeId.Thread -> listOf(SessionTreeId.Project(selectedTreeId.projectPath))
    is SessionTreeId.SubAgent -> listOf(
      SessionTreeId.Project(selectedTreeId.projectPath),
      SessionTreeId.Thread(selectedTreeId.projectPath, selectedTreeId.provider, selectedTreeId.threadId),
    )
    is SessionTreeId.WorktreeThread -> listOf(
      SessionTreeId.Project(selectedTreeId.projectPath),
      SessionTreeId.Worktree(selectedTreeId.projectPath, selectedTreeId.worktreePath),
    )
    is SessionTreeId.WorktreeSubAgent -> listOf(
      SessionTreeId.Project(selectedTreeId.projectPath),
      SessionTreeId.Worktree(selectedTreeId.projectPath, selectedTreeId.worktreePath),
      SessionTreeId.WorktreeThread(
        selectedTreeId.projectPath,
        selectedTreeId.worktreePath,
        selectedTreeId.provider,
        selectedTreeId.threadId,
      ),
    )
    else -> emptyList()
  }
}

internal fun formatRelativeTimeShort(timestamp: Long, now: Long): String {
  val absSeconds = abs(((timestamp - now) / 1000.0).roundToLong())
  if (absSeconds < 60) {
    return AgentSessionsBundle.message("toolwindow.time.now")
  }
  if (absSeconds < 60 * 60) {
    val value = max(1, (absSeconds / 60.0).roundToLong())
    return "${value}m"
  }
  if (absSeconds < 60 * 60 * 24) {
    val value = max(1, (absSeconds / (60.0 * 60.0)).roundToLong())
    return "${value}h"
  }
  if (absSeconds < 60 * 60 * 24 * 7) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0)).roundToLong())
    return "${value}d"
  }
  if (absSeconds < 60 * 60 * 24 * 30) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 7.0)).roundToLong())
    return "${value}w"
  }
  if (absSeconds < 60 * 60 * 24 * 365) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 30.0)).roundToLong())
    return "${value}mo"
  }
  val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 365.0)).roundToLong())
  return "${value}y"
}

internal fun threadDisplayTitle(thread: AgentSessionThread): @NlsSafe String {
  return threadDisplayTitle(threadId = thread.id, title = thread.title)
}

internal fun threadDisplayTitle(threadId: String, title: String): @NlsSafe String {
  val normalized = title
    .replace('\n', ' ')
    .replace('\r', ' ')
    .replace(THREAD_TITLE_WHITESPACE, " ")
    .trim()
  if (normalized.isNotEmpty()) {
    return normalized
  }
  val idPrefix = threadId.trim().takeIf { it.isNotEmpty() }?.take(8) ?: "unknown"
  return AgentSessionsBundle.message("toolwindow.thread.fallback.title", idPrefix)
}
