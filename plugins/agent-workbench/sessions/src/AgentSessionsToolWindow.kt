package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetSettings
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun agentSessionsToolWindow(currentProject: Project) {
  val service = remember { service<AgentSessionsService>() }
  val chatSelectionService = remember(currentProject) { currentProject.service<AgentChatTabSelectionService>() }
  val uiStateService = remember { service<AgentSessionsTreeUiStateService>() }
  val state by service.state.collectAsState()
  val selectedChatTab by chatSelectionService.selectedChatTab.collectAsState()
  val lastUsedProvider by uiStateService.lastUsedProviderFlow.collectAsState()
  val claudeQuotaHintEligible by uiStateService.claudeQuotaHintEligibleFlow.collectAsState()
  val claudeQuotaHintAcknowledged by uiStateService.claudeQuotaHintAcknowledgedFlow.collectAsState()
  val isClaudeQuotaWidgetEnabled by ClaudeQuotaStatusBarWidgetSettings.enabledFlow.collectAsState()

  LaunchedEffect(Unit) {
    service.refresh()
  }

  LaunchedEffect(Unit) {
    while (true) {
      ClaudeQuotaStatusBarWidgetSettings.syncEnabledState()
      delay(1.seconds)
    }
  }

  LaunchedEffect(claudeQuotaHintEligible, claudeQuotaHintAcknowledged, isClaudeQuotaWidgetEnabled) {
    if (claudeQuotaHintEligible && !claudeQuotaHintAcknowledged && isClaudeQuotaWidgetEnabled) {
      uiStateService.acknowledgeClaudeQuotaHint()
    }
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
    onCreateSession = { path, provider, mode -> service.createNewSession(path, provider, mode, currentProject) },
    onArchiveThread = { path, thread -> service.archiveThread(path, thread) },
    onArchiveThreads = { targets -> service.archiveThreads(targets) },
    canArchiveThread = { thread -> service.canArchiveThread(thread) },
    treeUiState = uiStateService,
    lastUsedProvider = lastUsedProvider,
    visibleClosedProjectCount = state.visibleClosedProjectCount,
    onShowMoreProjects = { service.showMoreProjects() },
    visibleThreadCounts = state.visibleThreadCounts,
    onShowMoreThreads = { path -> service.showMoreThreads(path) },
    selectedTreeId = selectedTreeId,
    showClaudeQuotaHint = claudeQuotaHintEligible && !claudeQuotaHintAcknowledged && !isClaudeQuotaWidgetEnabled,
    onEnableClaudeQuotaWidget = {
      ClaudeQuotaStatusBarWidgetSettings.setEnabled(true)
      uiStateService.acknowledgeClaudeQuotaHint()
    },
    onDismissClaudeQuotaHint = {
      uiStateService.acknowledgeClaudeQuotaHint()
    },
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
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit = { _, _, _ -> },
  onArchiveThread: (String, AgentSessionThread) -> Unit = { _, _ -> },
  onArchiveThreads: (List<ArchiveThreadTarget>) -> Unit = { targets ->
    targets.forEach { target -> onArchiveThread(target.path, target.thread) }
  },
  canArchiveThread: (AgentSessionThread) -> Boolean = { false },
  treeUiState: SessionsTreeUiState? = null,
  lastUsedProvider: AgentSessionProvider? = null,
  nowProvider: () -> Long = { System.currentTimeMillis() },
  visibleClosedProjectCount: Int = Int.MAX_VALUE,
  onShowMoreProjects: () -> Unit = {},
  visibleThreadCounts: Map<String, Int> = emptyMap(),
  onShowMoreThreads: (String) -> Unit = {},
  selectedTreeId: SessionTreeId? = null,
  showClaudeQuotaHint: Boolean = false,
  onEnableClaudeQuotaWidget: () -> Unit = {},
  onDismissClaudeQuotaHint: () -> Unit = {},
) {
  val effectiveTreeUiState = treeUiState ?: remember { InMemorySessionsTreeUiState() }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (showClaudeQuotaHint) {
      claudeQuotaHintBanner(
        onEnableClaudeQuotaWidget = onEnableClaudeQuotaWidget,
        onDismiss = onDismissClaudeQuotaHint,
      )
    }

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
        onArchiveThread = onArchiveThread,
        onArchiveThreads = onArchiveThreads,
        canArchiveThread = canArchiveThread,
        treeUiState = effectiveTreeUiState,
        lastUsedProvider = lastUsedProvider,
        nowProvider = nowProvider,
        visibleClosedProjectCount = visibleClosedProjectCount,
        onShowMoreProjects = onShowMoreProjects,
        visibleThreadCounts = visibleThreadCounts,
        onShowMoreThreads = onShowMoreThreads,
        selectedTreeId = selectedTreeId,
      )
    }
  }
}

@Composable
private fun claudeQuotaHintBanner(
  onEnableClaudeQuotaWidget: () -> Unit,
  onDismiss: () -> Unit,
) {
  val shape = RoundedCornerShape(8.dp)
  val borderColor = JewelTheme.globalColors.borders.normal
    .takeOrElse { JewelTheme.globalColors.text.disabled }
    .copy(alpha = 0.35f)
  val bodyColor = JewelTheme.globalColors.text.normal.copy(alpha = 0.84f)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .border(width = 1.dp, color = borderColor, shape = shape)
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = AgentSessionsBundle.message("toolwindow.claude.quota.hint.title"),
      style = AgentSessionsTextStyles.projectTitle(),
    )
    Text(
      text = AgentSessionsBundle.message("toolwindow.claude.quota.hint.body"),
      style = AgentSessionsTextStyles.threadTitle(),
      color = bodyColor,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onEnableClaudeQuotaWidget) {
        Text(AgentSessionsBundle.message("toolwindow.claude.quota.hint.enable"))
      }
      OutlinedButton(onClick = onDismiss) {
        Text(AgentSessionsBundle.message("toolwindow.claude.quota.hint.dismiss"))
      }
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
