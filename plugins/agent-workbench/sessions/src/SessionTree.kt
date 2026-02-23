package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.search.SpeedSearchableTree
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.theme.treeStyle

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun sessionTree(
  projects: List<AgentProjectSessions>,
  onRefresh: () -> Unit,
  onOpenProject: (String) -> Unit,
  onProjectExpanded: (String) -> Unit,
  onWorktreeExpanded: (String, String) -> Unit = { _, _ -> },
  onOpenThread: (String, AgentSessionThread) -> Unit,
  onOpenSubAgent: (String, AgentSessionThread, AgentSubAgent) -> Unit,
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit = { _, _, _ -> },
  onArchiveThread: (String, AgentSessionThread) -> Unit = { _, _ -> },
  onArchiveThreads: (List<ArchiveThreadTarget>) -> Unit = { targets ->
    targets.forEach { target -> onArchiveThread(target.path, target.thread) }
  },
  canArchiveThread: (AgentSessionThread) -> Boolean = { false },
  treeUiState: SessionsTreeUiState,
  lastUsedProvider: AgentSessionProvider? = null,
  nowProvider: () -> Long,
  visibleProjectCount: Int = Int.MAX_VALUE,
  onShowMoreProjects: () -> Unit = {},
  visibleThreadCounts: Map<String, Int> = emptyMap(),
  onShowMoreThreads: (String) -> Unit = {},
  selectedTreeId: SessionTreeId? = null,
) {
  val stateHolder = rememberSessionTreeStateHolder(
    onProjectExpanded = { path ->
      treeUiState.setProjectCollapsed(path, collapsed = false)
      onProjectExpanded(path)
    },
    onProjectCollapsed = { path ->
      treeUiState.setProjectCollapsed(path, collapsed = true)
    },
    onWorktreeExpanded = onWorktreeExpanded,
  )
  val treeState = stateHolder.treeState
  val pointerEventActions = remember(treeState) { SessionTreePointerEventActions(treeState) }
  var selectedArchiveTargets by remember { mutableStateOf<List<ArchiveThreadTarget>>(emptyList()) }
  val autoOpenNodes = remember(projects, visibleProjectCount, treeUiState) {
    projects.take(visibleProjectCount)
      .filter {
        it.isOpen ||
        it.errorMessage != null ||
        it.providerWarnings.isNotEmpty() ||
        it.worktrees.any { wt -> wt.isOpen }
      }
      .filterNot { treeUiState.isProjectCollapsed(it.path) }
      .map { SessionTreeId.Project(it.path) }
  }
  LaunchedEffect(autoOpenNodes) {
    stateHolder.applyDefaultOpenProjects(autoOpenNodes)
  }
  LaunchedEffect(selectedTreeId) {
    if (selectedTreeId == null) {
      if (treeState.selectedKeys.isNotEmpty()) {
        treeState.selectedKeys = emptySet()
      }
      return@LaunchedEffect
    }
    val parentNodes = parentNodesForSelection(selectedTreeId)
    if (parentNodes.isNotEmpty()) {
      treeState.openNodes(parentNodes)
    }
    treeState.selectedKeys = setOf(selectedTreeId)
  }
  val tree = remember(projects, visibleProjectCount, visibleThreadCounts) {
    buildSessionTree(projects, visibleProjectCount, visibleThreadCounts)
  }

  val treeStyle = run {
    val baseStyle = JewelTheme.treeStyle
    val metrics = baseStyle.metrics
    // Reduce indent to offset the chevron width so depth feels like a single step.
    val indentSize = (metrics.indentSize - metrics.simpleListItemMetrics.iconTextGap)
      .coerceAtLeast(metrics.indentSize * 0.5f)
    LazyTreeStyle(
      colors = baseStyle.colors,
      metrics = LazyTreeMetrics(
        indentSize = indentSize,
        elementMinHeight = metrics.elementMinHeight,
        chevronContentGap = metrics.chevronContentGap,
        simpleListItemMetrics = metrics.simpleListItemMetrics,
      ),
      icons = baseStyle.icons,
    )
  }
  SpeedSearchArea(Modifier.fillMaxSize()) {
    SpeedSearchableTree(
      tree = tree,
      modifier = Modifier.fillMaxSize().focusable(),
      treeState = treeState,
      nodeText = { element -> sessionTreeNodeText(element.data) },
      style = treeStyle,
      onElementClick = { element ->
        if (!pointerEventActions.consumeShouldOpenOnClick(element.id)) {
          return@SpeedSearchableTree
        }
        when (val node = element.data) {
          is SessionTreeNode.Project -> {
            onOpenProject(node.project.path)
          }
          is SessionTreeNode.Thread -> {
            val path = when (val id = element.id) {
              is SessionTreeId.WorktreeThread -> id.worktreePath
              else -> node.project.path
            }
            onOpenThread(path, node.thread)
          }
          is SessionTreeNode.SubAgent -> {
            val path = when (val id = element.id) {
              is SessionTreeId.WorktreeSubAgent -> id.worktreePath
              else -> node.project.path
            }
            onOpenSubAgent(path, node.thread, node.subAgent)
          }
          is SessionTreeNode.MoreProjects -> onShowMoreProjects()
          is SessionTreeNode.MoreThreads -> {
            val path = when (val id = element.id) {
              is SessionTreeId.WorktreeMoreThreads -> id.worktreePath
              is SessionTreeId.MoreThreads -> id.projectPath
              else -> null
            }
            if (path != null) onShowMoreThreads(path)
          }
          is SessionTreeNode.Warning -> Unit
          is SessionTreeNode.Error -> Unit
          is SessionTreeNode.Empty -> Unit
          is SessionTreeNode.Worktree -> onOpenProject(node.worktree.path)
        }
      },
      onElementDoubleClick = {},
      onSelectionChange = { selectedElements ->
        selectedArchiveTargets = resolveSelectedArchiveThreadTargets(selectedElements)
      },
      pointerEventActions = pointerEventActions,
    ) { element ->
      sessionTreeNodeContent(
        element = element,
        onOpenProject = onOpenProject,
        onRefresh = onRefresh,
        onCreateSession = onCreateSession,
        onArchiveThreads = onArchiveThreads,
        selectedArchiveTargets = selectedArchiveTargets,
        canArchiveThread = canArchiveThread,
        lastUsedProvider = lastUsedProvider,
        nowProvider = nowProvider,
      )
    }
  }
}

private fun resolveSelectedArchiveThreadTargets(
  selectedElements: List<Tree.Element<SessionTreeNode>>,
): List<ArchiveThreadTarget> {
  val targetsByKey = LinkedHashMap<String, ArchiveThreadTarget>()
  selectedElements.forEach { element ->
    val node = element.data as? SessionTreeNode.Thread ?: return@forEach
    val path = when (val id = element.id) {
      is SessionTreeId.WorktreeThread -> id.worktreePath
      is SessionTreeId.Thread -> id.projectPath
      else -> node.project.path
    }
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val target = ArchiveThreadTarget(path = normalizedPath, thread = node.thread)
    val key = "$normalizedPath:${node.thread.provider}:${node.thread.id}"
    targetsByKey.putIfAbsent(key, target)
  }
  return targetsByKey.values.toList()
}

private fun buildSessionTree(
  projects: List<AgentProjectSessions>,
  visibleProjectCount: Int,
  visibleThreadCounts: Map<String, Int>,
): Tree<SessionTreeNode> =
  buildTree {
    val visibleProjects = projects.take(visibleProjectCount)
    val hiddenCount = (projects.size - visibleProjectCount).coerceAtLeast(0)
    visibleProjects.forEach { project ->
      val projectId = SessionTreeId.Project(project.path)
      addNode(
        data = SessionTreeNode.Project(project),
        id = projectId,
      ) {
        val errorMessage = project.errorMessage
        val hasVisibleWorktrees = project.worktrees.any {
          it.threads.isNotEmpty() || it.isLoading || it.errorMessage != null || it.providerWarnings.isNotEmpty()
        }
        if (errorMessage != null) {
          addLeaf(
            data = SessionTreeNode.Error(project, errorMessage),
            id = SessionTreeId.Error(project.path),
          )
        }
        else if (project.hasLoaded && project.threads.isEmpty() && !hasVisibleWorktrees && project.providerWarnings.isEmpty()) {
          addLeaf(
            data = SessionTreeNode.Empty(project, AgentSessionsBundle.message("toolwindow.empty.project")),
            id = SessionTreeId.Empty(project.path),
          )
        }
        else {
          addProviderWarningNodes(project.providerWarnings) { provider ->
            SessionTreeId.Warning(project.path, provider)
          }
          val visibleWorktrees = project.worktrees.filter {
            it.threads.isNotEmpty() || it.isLoading || it.errorMessage != null || it.providerWarnings.isNotEmpty()
          }
          visibleWorktrees.forEach { worktree ->
            addNode(
              data = SessionTreeNode.Worktree(project, worktree),
              id = SessionTreeId.Worktree(project.path, worktree.path),
            ) {
              val wtError = worktree.errorMessage
              if (wtError != null) {
                addLeaf(
                  data = SessionTreeNode.Error(project, wtError),
                  id = SessionTreeId.WorktreeError(project.path, worktree.path),
                )
              }
              else {
                addProviderWarningNodes(worktree.providerWarnings) { provider ->
                  SessionTreeId.WorktreeWarning(project.path, worktree.path, provider)
                }
                val visibleCount = visibleThreadCounts[worktree.path] ?: DEFAULT_VISIBLE_THREAD_COUNT
                addThreadNodes(
                  project = project,
                  threads = worktree.threads,
                  maxVisible = visibleCount,
                  parentWorktreeBranch = worktree.branch,
                  worktreePath = worktree.path,
                ) { provider, threadId ->
                  SessionTreeId.WorktreeThread(project.path, worktree.path, provider, threadId)
                }
                if (worktree.threads.size > visibleCount) {
                  addLeaf(
                    data = SessionTreeNode.MoreThreads(
                      project = project,
                      hiddenCount = if (worktree.hasUnknownThreadCount) null else worktree.threads.size - visibleCount,
                    ),
                    id = SessionTreeId.WorktreeMoreThreads(project.path, worktree.path),
                  )
                }
              }
            }
          }
          val visibleCount = visibleThreadCounts[project.path] ?: DEFAULT_VISIBLE_THREAD_COUNT
          addThreadNodes(project, project.threads, visibleCount) { provider, threadId ->
            SessionTreeId.Thread(project.path, provider, threadId)
          }
          if (project.threads.size > visibleCount) {
            addLeaf(
              data = SessionTreeNode.MoreThreads(
                project = project,
                hiddenCount = if (project.hasUnknownThreadCount) null else project.threads.size - visibleCount,
              ),
              id = SessionTreeId.MoreThreads(project.path),
            )
          }
        }
      }
    }
    if (hiddenCount > 0) {
      addLeaf(
        data = SessionTreeNode.MoreProjects(hiddenCount),
        id = SessionTreeId.MoreProjects,
      )
    }
  }

private fun TreeGeneratorScope<SessionTreeNode>.addThreadNodes(
  project: AgentProjectSessions,
  threads: List<AgentSessionThread>,
  maxVisible: Int = Int.MAX_VALUE,
  parentWorktreeBranch: String? = null,
  worktreePath: String? = null,
  threadIdFactory: (AgentSessionProvider, String) -> SessionTreeId,
) {
  threads.take(maxVisible).forEach { thread ->
    val threadId = threadIdFactory(thread.provider, thread.id)
    val threadNode = SessionTreeNode.Thread(project, thread, parentWorktreeBranch)
    if (thread.subAgents.isNotEmpty()) {
      addNode(
        data = threadNode,
        id = threadId,
      ) {
        thread.subAgents.forEach { subAgent ->
          val subAgentId = if (worktreePath != null) {
            SessionTreeId.WorktreeSubAgent(project.path, worktreePath, thread.provider, thread.id, subAgent.id)
          }
          else {
            SessionTreeId.SubAgent(project.path, thread.provider, thread.id, subAgent.id)
          }
          addLeaf(
            data = SessionTreeNode.SubAgent(project, thread, subAgent),
            id = subAgentId,
          )
        }
      }
    }
    else {
      addLeaf(
        data = threadNode,
        id = threadId,
      )
    }
  }
}

private fun TreeGeneratorScope<SessionTreeNode>.addProviderWarningNodes(
  warnings: List<AgentSessionProviderWarning>,
  warningIdFactory: (AgentSessionProvider) -> SessionTreeId,
) {
  warnings.forEach { warning ->
    addLeaf(
      data = SessionTreeNode.Warning(warning.message),
      id = warningIdFactory(warning.provider),
    )
  }
}

private fun sessionTreeNodeText(node: SessionTreeNode): String? =
  when (node) {
    is SessionTreeNode.Project -> node.project.name
    is SessionTreeNode.Thread -> node.thread.title
    is SessionTreeNode.SubAgent -> node.subAgent.name.ifBlank { node.subAgent.id }
    is SessionTreeNode.Warning -> null
    is SessionTreeNode.Error -> null
    is SessionTreeNode.Empty -> node.message
    is SessionTreeNode.MoreProjects -> null
    is SessionTreeNode.MoreThreads -> null
    is SessionTreeNode.Worktree -> node.worktree.name
  }

internal sealed interface SessionTreeNode {
  data class Project(val project: AgentProjectSessions) : SessionTreeNode
  data class Thread(val project: AgentProjectSessions, val thread: AgentSessionThread, val parentWorktreeBranch: String? = null) : SessionTreeNode
  data class SubAgent(
    val project: AgentProjectSessions,
    val thread: AgentSessionThread,
    val subAgent: AgentSubAgent,
  ) : SessionTreeNode
  data class Warning(val message: String) : SessionTreeNode
  data class Error(val project: AgentProjectSessions, val message: String) : SessionTreeNode
  data class Empty(val project: AgentProjectSessions, val message: String) : SessionTreeNode
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

private fun parentNodesForSelection(selectedTreeId: SessionTreeId): List<SessionTreeId> {
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
