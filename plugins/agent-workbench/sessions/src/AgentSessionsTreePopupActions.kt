// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

internal const val AGENT_SESSIONS_TREE_POPUP_ACTION_GROUP_ID: String = "AgentWorkbenchSessions.TreePopup"
internal const val AGENT_SESSIONS_TREE_POPUP_NEW_THREAD_GROUP_ID: String = "AgentWorkbenchSessions.TreePopup.NewThread"

internal object AgentSessionsTreePopupDataKeys {
  @JvmField
  val CONTEXT: DataKey<AgentSessionsTreePopupActionContext> =
    DataKey.create("agent.workbench.sessions.tree.popup.context")
}

internal data class AgentSessionsTreePopupActionContext(
  val project: Project,
  val nodeId: SessionTreeId,
  val node: SessionTreeNode,
  val archiveTargets: List<ArchiveThreadTarget>,
)

internal fun resolveAgentSessionsTreePopupActionContext(event: AnActionEvent): AgentSessionsTreePopupActionContext? {
  return event.getData(AgentSessionsTreePopupDataKeys.CONTEXT)
}

internal class AgentSessionsTreePopupOpenAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val openProject: (String) -> Unit
  private val openThread: (String, AgentSessionThread, Project) -> Unit
  private val openSubAgent: (String, AgentSessionThread, AgentSubAgent, Project) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    openProject = { path -> service<AgentSessionsService>().openOrFocusProject(path) }
    openThread = { path, thread, project -> service<AgentSessionsService>().openChatThread(path, thread, project) }
    openSubAgent = { path, thread, subAgent, project ->
      service<AgentSessionsService>().openChatSubAgent(path, thread, subAgent, project)
    }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    openProject: (String) -> Unit,
    openThread: (String, AgentSessionThread, Project) -> Unit,
    openSubAgent: (String, AgentSessionThread, AgentSubAgent, Project) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.openProject = openProject
    this.openThread = openThread
    this.openSubAgent = openSubAgent
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val canOpen = when (context?.node) {
      is SessionTreeNode.Project -> !context.node.project.isOpen
      is SessionTreeNode.Worktree,
      is SessionTreeNode.Thread,
      is SessionTreeNode.SubAgent -> true
      else -> false
    }
    e.presentation.isEnabledAndVisible = canOpen
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (val node = context.node) {
      is SessionTreeNode.Project -> openProject(node.project.path)
      is SessionTreeNode.Worktree -> openProject(node.worktree.path)
      is SessionTreeNode.Thread -> {
        val path = pathForThreadNode(context.nodeId, node.project.path)
        openThread(path, node.thread, context.project)
      }
      is SessionTreeNode.SubAgent -> {
        val path = pathForThreadNode(context.nodeId, node.project.path)
        openSubAgent(path, node.thread, node.subAgent, context.project)
      }
      else -> Unit
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupMoreAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val showMoreProjects: () -> Unit
  private val showMoreThreads: (String) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    showMoreProjects = { service<AgentSessionsService>().showMoreProjects() }
    showMoreThreads = { path -> service<AgentSessionsService>().showMoreThreads(path) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    showMoreProjects: () -> Unit,
    showMoreThreads: (String) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.showMoreProjects = showMoreProjects
    this.showMoreThreads = showMoreThreads
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val node = context?.node
    if (node is SessionTreeNode.MoreProjects || node is SessionTreeNode.MoreThreads) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = morePopupLabel(node)
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (context.node) {
      is SessionTreeNode.MoreProjects -> showMoreProjects()
      is SessionTreeNode.MoreThreads -> {
        val path = pathForMoreThreadsNode(context.nodeId) ?: return
        showMoreThreads(path)
      }
      else -> Unit
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupNewThreadGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext,
  private val allBridges: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit = ::createNewThreadViaService,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionsTreeUiStateService>().getLastUsedProvider() },
) : ActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val path = context?.let(::newThreadPathFromNode)
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider())
    if (path == null || !actionModel.menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = actionModel.quickStartItem != null
    e.presentation.icon = actionModel.quickStartItem?.let { providerIcon(it.bridge.provider) } ?: templatePresentation.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val path = newThreadPathFromNode(context) ?: return
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider())
    launchQuickStartThread(path, context.project, actionModel.quickStartItem, createNewSession)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val context = e?.let(resolveContext) ?: return emptyArray()
    val path = newThreadPathFromNode(context) ?: return emptyArray()
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider())
    return buildNewThreadMenuActions(
      path = path,
      project = context.project,
      menuModel = actionModel.menuModel,
      createNewSession = createNewSession,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun newThreadPathFromNode(context: AgentSessionsTreePopupActionContext): String? {
  return when (val node = context.node) {
    is SessionTreeNode.Project -> node.project.path
    is SessionTreeNode.Worktree -> node.worktree.path
    else -> null
  }
}

private fun morePopupLabel(node: SessionTreeNode): @Nls String {
  return when (node) {
    is SessionTreeNode.MoreProjects -> AgentSessionsBundle.message("toolwindow.action.more.count", node.hiddenCount)
    is SessionTreeNode.MoreThreads ->
      node.hiddenCount?.let { AgentSessionsBundle.message("toolwindow.action.more.count", it) }
      ?: AgentSessionsBundle.message("toolwindow.action.more")
    else -> AgentSessionsBundle.message("toolwindow.action.more")
  }
}
