package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-thread-visibility.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.search.highlightSpeedSearchMatches
import org.jetbrains.jewel.ui.component.search.highlightTextSearch

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun SelectableLazyItemScope.sessionTreeNodeContent(
  element: Tree.Element<SessionTreeNode>,
  onOpenProject: (String) -> Unit,
  onRefresh: () -> Unit,
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit = { _, _, _ -> },
  onArchiveThreads: (List<ArchiveThreadTarget>) -> Unit = {},
  selectedArchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  canArchiveThread: (AgentSessionThread) -> Boolean = { false },
  lastUsedProvider: AgentSessionProvider? = null,
  nowProvider: () -> Long,
) {
  val node = element.data
  when (node) {
    is SessionTreeNode.Project -> projectNodeRow(
      project = node.project,
      onOpenProject = onOpenProject,
      onCreateSession = onCreateSession,
      lastUsedProvider = lastUsedProvider,
    )
    is SessionTreeNode.Thread -> {
      val path = when (val id = element.id) {
        is SessionTreeId.WorktreeThread -> id.worktreePath
        else -> node.project.path
      }
      threadNodeRow(
        path = path,
        thread = node.thread,
        nowProvider = nowProvider,
        parentWorktreeBranch = node.parentWorktreeBranch,
        selectedArchiveTargets = selectedArchiveTargets,
        canArchiveThread = canArchiveThread,
        onArchiveThreads = onArchiveThreads,
      )
    }
    is SessionTreeNode.SubAgent -> subAgentNodeRow(
      subAgent = node.subAgent,
    )
    is SessionTreeNode.Warning -> warningNodeRow(
      message = node.message,
    )
    is SessionTreeNode.Error -> errorNodeRow(
      message = node.message,
      onRetry = onRefresh,
    )
    is SessionTreeNode.Empty -> emptyNodeRow(
      message = node.message,
    )
    is SessionTreeNode.MoreProjects -> moreProjectsRow(
      hiddenCount = node.hiddenCount,
    )
    is SessionTreeNode.MoreThreads -> moreThreadsRow(
      hiddenCount = node.hiddenCount,
    )
    is SessionTreeNode.Worktree -> worktreeNodeRow(
      worktree = node.worktree,
      onCreateSession = onCreateSession,
      lastUsedProvider = lastUsedProvider,
    )
  }
}

private data class TreeRowChrome(
  val interactionSource: MutableInteractionSource,
  val isHovered: Boolean,
  val background: Color,
  val shape: Shape,
  val spacing: Dp,
  val indicatorPadding: Dp,
)

@Composable
private fun rememberTreeRowChrome(
  isSelected: Boolean,
  isActive: Boolean,
  baseTint: Color = Color.Unspecified,
  baseTintAlpha: Float = 0.06f,
): TreeRowChrome {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val background = treeRowBackground(
    isHovered = isHovered,
    isSelected = isSelected,
    isActive = isActive,
    baseTint = baseTint,
    baseTintAlpha = baseTintAlpha,
  )
  val shape = treeRowShape()
  val spacing = treeRowSpacing()
  val indicatorPadding = spacing * 0.4f
  return TreeRowChrome(
    interactionSource = interactionSource,
    isHovered = isHovered,
    background = background,
    shape = shape,
    spacing = spacing,
    indicatorPadding = indicatorPadding,
  )
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SelectableLazyItemScope.projectNodeRow(
  project: AgentProjectSessions,
  onOpenProject: (String) -> Unit,
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit,
  lastUsedProvider: AgentSessionProvider?,
) {
  val isProjectOpen = project.isOpen || project.worktrees.any { worktree -> worktree.isOpen }
  val chrome = rememberTreeRowChrome(isSelected = isSelected, isActive = isActive)
  val openLabel = AgentSessionsBundle.message("toolwindow.action.open")
  val titleColor = if (isSelected || isActive) {
    Color.Unspecified
  }
  else if (isProjectOpen) {
    JewelTheme.globalColors.text.normal
  }
  else {
    JewelTheme.globalColors.text.normal.copy(alpha = 0.72f)
  }
  val branchColor = LocalContentColor.current
    .takeOrElse { JewelTheme.globalColors.text.disabled }
    .copy(alpha = if (isProjectOpen) 0.55f else 0.42f)
  ContextMenuArea(
    items = {
      if (!project.isOpen) {
        listOf(
          ContextMenuItemOption(
            label = openLabel,
            action = { onOpenProject(project.path) },
          ),
        )
      } else {
        emptyList()
      }
    },
    enabled = !project.isOpen,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(chrome.background, chrome.shape)
        .hoverable(chrome.interactionSource),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(chrome.spacing)
    ) {
      val projectTitle = project.name
      var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
      val isTitleOverflowed = titleLayoutResult?.hasVisualOverflow == true
      Box(modifier = Modifier.weight(1f)) {
        Tooltip(
          tooltip = { Text(projectTitle) },
          enabled = isTitleOverflowed,
        ) {
          Text(
            text = projectTitle.highlightTextSearch(),
            style = AgentSessionsTextStyles.projectTitle(isOpen = isProjectOpen),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            onTextLayout = { titleLayoutResult = it },
            modifier = Modifier.highlightSpeedSearchMatches(titleLayoutResult),
          )
        }
      }
      if (project.worktrees.isNotEmpty()) {
        val branchLabel = project.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached")
        Text(
          text = "[$branchLabel]",
          color = branchColor,
          style = AgentSessionsTextStyles.threadTime(),
          maxLines = 1,
        )
      }
      var newSessionPopupVisible by remember { mutableStateOf(false) }
      if (project.isLoading) {
        CircularProgressIndicator(Modifier.size(loadingIndicatorSize()))
      }
      else if (chrome.isHovered || newSessionPopupVisible) {
        NewSessionHoverActions(
          path = project.path,
          lastUsedProvider = lastUsedProvider,
          onCreateSession = onCreateSession,
          popupVisible = newSessionPopupVisible,
          onPopupVisibleChange = { newSessionPopupVisible = it },
        )
      }
    }
  }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SelectableLazyItemScope.threadNodeRow(
  path: String,
  thread: AgentSessionThread,
  nowProvider: () -> Long,
  parentWorktreeBranch: String? = null,
  selectedArchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  canArchiveThread: (AgentSessionThread) -> Boolean = { false },
  onArchiveThreads: ((List<ArchiveThreadTarget>) -> Unit)? = null,
) {
  val timestamp = thread.updatedAt.takeIf { it > 0 }
  val timeLabel = timestamp?.let { formatRelativeTimeShort(it, nowProvider()) }
  val chrome = rememberTreeRowChrome(isSelected = isSelected, isActive = isActive)
  val originBranch = thread.originBranch
  val branchMismatch = originBranch != null && parentWorktreeBranch != null && originBranch != parentWorktreeBranch
  val titleColor = if (isSelected || isActive) Color.Unspecified else {
    JewelTheme.globalColors.text.normal.copy(alpha = 0.84f)
  }
  val timeColor = LocalContentColor.current
    .takeOrElse { JewelTheme.globalColors.text.disabled }
    .copy(alpha = 0.55f)
  val providerLabel = providerLabel(thread.provider)
  val indicatorColor = if (branchMismatch) JewelTheme.globalColors.text.warning else threadIndicatorColor(thread)
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  val currentArchiveTarget = ArchiveThreadTarget(path = normalizedPath, thread = thread)
  val useSelectedTargets = selectedArchiveTargets.size > 1 && selectedArchiveTargets.any { target ->
    target.path == normalizedPath && target.thread.provider == thread.provider && target.thread.id == thread.id
  }
  val effectiveArchiveTargets = if (useSelectedTargets) selectedArchiveTargets else listOf(currentArchiveTarget)
  val canArchiveAction = effectiveArchiveTargets.any { target -> canArchiveThread(target.thread) }
  val archiveLabel = if (effectiveArchiveTargets.size > 1) {
    AgentSessionsBundle.message("toolwindow.action.archive.selected.count", effectiveArchiveTargets.size)
  }
  else {
    AgentSessionsBundle.message("toolwindow.action.archive")
  }
  val threadTitle = thread.title
  var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  val isTitleOverflowed = titleLayoutResult?.hasVisualOverflow == true
  val branchMismatchText = AgentSessionsBundle.message("toolwindow.thread.branch.mismatch", originBranch ?: "")
  val unifiedTooltipText = when {
    branchMismatch && isTitleOverflowed -> "$branchMismatchText\n$threadTitle"
    branchMismatch -> branchMismatchText
    isTitleOverflowed -> threadTitle
    else -> null
  }
  Tooltip(
    tooltip = {
      Text(unifiedTooltipText ?: "")
    },
    enabled = unifiedTooltipText != null,
  ) {
    val rowContent: @Composable () -> Unit = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(chrome.background, chrome.shape)
          .hoverable(chrome.interactionSource),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(chrome.spacing)
      ) {
        Box(
          modifier = Modifier
            .padding(end = chrome.indicatorPadding)
            .size(threadIndicatorSize())
            .background(indicatorColor, CircleShape)
        )
        Text(
          text = threadTitle.highlightTextSearch(),
          style = AgentSessionsTextStyles.threadTitle(),
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
          onTextLayout = { titleLayoutResult = it },
          modifier = Modifier
            .weight(1f)
            .highlightSpeedSearchMatches(titleLayoutResult),
        )
        Text(
          text = providerLabel,
          color = timeColor,
          style = AgentSessionsTextStyles.threadTime(),
        )
        if (timeLabel != null) {
          Text(
            text = timeLabel,
            color = timeColor,
            style = AgentSessionsTextStyles.threadTime(),
          )
        }
      }
    }

    if (canArchiveAction && onArchiveThreads != null) {
      ContextMenuArea(
        items = {
          listOf(
            ContextMenuItemOption(
              label = archiveLabel,
              action = { onArchiveThreads(effectiveArchiveTargets) },
            ),
          )
        },
        enabled = true,
      ) {
        rowContent()
      }
    }
    else {
      rowContent()
    }
  }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SelectableLazyItemScope.subAgentNodeRow(
  subAgent: AgentSubAgent,
) {
  val chrome = rememberTreeRowChrome(isSelected = isSelected, isActive = isActive)
  val displayName = subAgent.name.ifBlank { subAgent.id }
  val titleColor = if (isSelected || isActive) Color.Unspecified else JewelTheme.globalColors.text.disabled
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(chrome.background, chrome.shape)
      .hoverable(chrome.interactionSource),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(chrome.spacing)
  ) {
    Box(
      modifier = Modifier
        .padding(end = chrome.indicatorPadding)
        .size(subAgentIndicatorSize())
        .background(subAgentIndicatorColor(), CircleShape)
    )
    var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val isTitleOverflowed = titleLayoutResult?.hasVisualOverflow == true
    Box(modifier = Modifier.weight(1f)) {
      Tooltip(
        tooltip = { Text(displayName) },
        enabled = isTitleOverflowed,
      ) {
        Text(
          text = displayName.highlightTextSearch(),
          style = AgentSessionsTextStyles.subAgentTitle(),
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
          onTextLayout = { titleLayoutResult = it },
          modifier = Modifier.highlightSpeedSearchMatches(titleLayoutResult),
        )
      }
    }
  }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SelectableLazyItemScope.worktreeNodeRow(
  worktree: AgentWorktree,
  onCreateSession: (String, AgentSessionProvider, AgentSessionLaunchMode) -> Unit,
  lastUsedProvider: AgentSessionProvider?,
) {
  val chrome = rememberTreeRowChrome(isSelected = isSelected, isActive = isActive)
  val titleColor = if (isSelected || isActive) Color.Unspecified else {
    JewelTheme.globalColors.text.normal.copy(alpha = 0.84f)
  }
  val branchColor = LocalContentColor.current
    .takeOrElse { JewelTheme.globalColors.text.disabled }
    .copy(alpha = 0.55f)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(chrome.background, chrome.shape)
      .hoverable(chrome.interactionSource),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(chrome.spacing)
  ) {
    val worktreeTitle = worktree.name
    var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val isTitleOverflowed = titleLayoutResult?.hasVisualOverflow == true
    Box(modifier = Modifier.weight(1f)) {
      Tooltip(
        tooltip = { Text(worktreeTitle) },
        enabled = isTitleOverflowed,
      ) {
        Text(
          text = worktreeTitle.highlightTextSearch(),
          style = AgentSessionsTextStyles.projectTitle(),
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
          onTextLayout = { titleLayoutResult = it },
          modifier = Modifier.highlightSpeedSearchMatches(titleLayoutResult),
        )
      }
    }
    val branchLabel = worktree.branch ?: AgentSessionsBundle.message("toolwindow.worktree.detached")
    Text(
      text = "[$branchLabel]",
      color = branchColor,
      style = AgentSessionsTextStyles.threadTime(),
      maxLines = 1,
    )
    var newSessionPopupVisible by remember { mutableStateOf(false) }
    val isHovered by chrome.interactionSource.collectIsHoveredAsState()
    if ((isHovered || newSessionPopupVisible) && !worktree.isLoading) {
      NewSessionHoverActions(
        path = worktree.path,
        lastUsedProvider = lastUsedProvider,
        onCreateSession = onCreateSession,
        popupVisible = newSessionPopupVisible,
        onPopupVisibleChange = { newSessionPopupVisible = it },
      )
    }
    else if (worktree.isLoading) {
      CircularProgressIndicator(Modifier.size(loadingIndicatorSize()))
    }
  }
}

private fun providerLabel(provider: AgentSessionProvider): String {
  return providerDisplayName(provider)
}

@Composable
private fun warningNodeRow(message: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = message,
      color = JewelTheme.globalColors.text.warning,
      style = AgentSessionsTextStyles.error(),
    )
  }
}

@Composable
private fun errorNodeRow(message: String, onRetry: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth()) {
    val rowSpacing = treeRowSpacing()
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
      Text(
        text = message,
        color = JewelTheme.globalColors.text.warning,
        style = AgentSessionsTextStyles.error(),
      )
      OutlinedButton(onClick = onRetry) {
        Text(AgentSessionsBundle.message("toolwindow.error.retry"))
      }
    }
  }
}

@Composable
private fun emptyNodeRow(message: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = message,
      color = JewelTheme.globalColors.text.disabled,
      style = AgentSessionsTextStyles.emptyState(),
    )
  }
}

@Composable
private fun moreProjectsRow(hiddenCount: Int) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = AgentSessionsBundle.message("toolwindow.action.more.count", hiddenCount),
      color = JewelTheme.globalColors.text.info,
      style = AgentSessionsTextStyles.threadTitle(),
    )
  }
}

@Composable
private fun moreThreadsRow(hiddenCount: Int?) {
  Row(modifier = Modifier.fillMaxWidth()) {
    val messageKey = if (hiddenCount == null) "toolwindow.action.more" else "toolwindow.action.more.count"
    val message = if (hiddenCount == null) {
      AgentSessionsBundle.message(messageKey)
    }
    else {
      AgentSessionsBundle.message(messageKey, hiddenCount)
    }
    Text(
      text = message,
      color = JewelTheme.globalColors.text.info,
      style = AgentSessionsTextStyles.threadTitle(),
    )
  }
}
