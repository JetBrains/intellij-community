package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun agentSessionsToolWindow(currentProject: Project) {
  val service = remember { service<AgentSessionsService>() }
  val chatSelectionService = remember(currentProject) { currentProject.service<AgentChatTabSelectionService>() }
  val uiStateService = remember { service<AgentSessionsTreeUiStateService>() }
  val state by service.state.collectAsState()
  val selectedChatTab by chatSelectionService.selectedChatTab.collectAsState()
  val lastUsedProvider by uiStateService.lastUsedProviderFlow.collectAsState()

  LaunchedEffect(Unit) {
    service.refresh()
  }

  LaunchedEffect(selectedChatTab, state.projects) {
    val currentSelection = selectedChatTab ?: return@LaunchedEffect
    val identity = parseAgentSessionIdentity(currentSelection.threadIdentity) ?: return@LaunchedEffect
    service.ensureThreadVisible(
      path = currentSelection.projectPath,
      provider = identity.provider,
      threadId = identity.sessionId,
    )
  }

  val selectedTreeId = remember(state.projects, selectedChatTab) {
    resolveSelectedSessionTreeId(state.projects, selectedChatTab)
  }

  agentSessionsToolWindowContent(
    state = state,
    onRefresh = { service.refresh() },
    onOpenProject = { service.openOrFocusProject(it) },
    onProjectExpanded = { service.loadProjectThreadsOnDemand(it) },
    onWorktreeExpanded = { projectPath, worktreePath ->
      service.loadWorktreeThreadsOnDemand(projectPath, worktreePath)
    },
    onOpenThread = { path, thread -> service.openChatThread(path, thread, currentProject) },
    onOpenSubAgent = { path, thread, subAgent -> service.openChatSubAgent(path, thread, subAgent, currentProject) },
    onCreateSession = { path, provider, yolo -> service.createNewSession(path, provider, yolo, currentProject) },
    lastUsedProvider = lastUsedProvider,
    visibleProjectCount = state.visibleProjectCount,
    onShowMoreProjects = { service.showMoreProjects() },
    visibleThreadCounts = state.visibleThreadCounts,
    onShowMoreThreads = { path -> service.showMoreThreads(path) },
    selectedTreeId = selectedTreeId,
  )
}

@Composable
internal fun agentSessionsToolWindowContent(
  state: AgentSessionsState,
  onRefresh: () -> Unit,
  onOpenProject: (String) -> Unit,
  onProjectExpanded: (String) -> Unit = {},
  onWorktreeExpanded: (String, String) -> Unit = { _, _ -> },
  onOpenThread: (String, AgentSessionThread) -> Unit = { _, _ -> },
  onOpenSubAgent: (String, AgentSessionThread, AgentSubAgent) -> Unit = { _, _, _ -> },
  onCreateSession: (String, AgentSessionProvider, Boolean) -> Unit = { _, _, _ -> },
  lastUsedProvider: AgentSessionProvider? = null,
  nowProvider: () -> Long = { System.currentTimeMillis() },
  visibleProjectCount: Int = Int.MAX_VALUE,
  onShowMoreProjects: () -> Unit = {},
  visibleThreadCounts: Map<String, Int> = emptyMap(),
  onShowMoreThreads: (String) -> Unit = {},
  selectedTreeId: SessionTreeId? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    when {
      state.projects.isEmpty() -> emptyState(isLoading = state.lastUpdatedAt == null)
      else -> sessionTree(
        projects = state.projects,
        onRefresh = onRefresh,
        onOpenProject = onOpenProject,
        onProjectExpanded = onProjectExpanded,
        onWorktreeExpanded = onWorktreeExpanded,
        onOpenThread = onOpenThread,
        onOpenSubAgent = onOpenSubAgent,
        onCreateSession = onCreateSession,
        lastUsedProvider = lastUsedProvider,
        nowProvider = nowProvider,
        visibleProjectCount = visibleProjectCount,
        onShowMoreProjects = onShowMoreProjects,
        visibleThreadCounts = visibleThreadCounts,
        onShowMoreThreads = onShowMoreThreads,
        selectedTreeId = selectedTreeId,
      )
    }
  }
}

@Composable
private fun emptyState(isLoading: Boolean) {
  val messageKey = if (isLoading) "toolwindow.loading" else "toolwindow.empty.global"
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = AgentSessionsBundle.message(messageKey),
      color = JewelTheme.globalColors.text.disabled,
      style = AgentSessionsTextStyles.emptyState(),
    )
  }
}
