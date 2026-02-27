// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
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
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

internal const val AGENT_SESSIONS_TREE_POPUP_ACTION_GROUP_ID: String = "AgentWorkbenchSessions.TreePopup"

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
  private val resolveEditorContext: (AnActionEvent) -> AgentChatEditorTabActionContext? =
    ::resolveAgentChatEditorTabActionContext,
  private val allBridges: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit =
    { path: String, provider: AgentSessionProvider, mode: AgentSessionLaunchMode, currentProject: Project ->
      service<AgentSessionsService>().createNewSession(path, provider, mode, currentProject)
    },
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionsTreeUiStateService>().getLastUsedProvider() },
) : ActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val launchContext = resolveLaunchContext(e)
    val menuModel = buildNewSessionMenuModel()
    val quickStartItem = resolveQuickStartItem(menuModel)
    if (launchContext == null || quickStartItem == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = true
    e.presentation.icon = providerIcon(quickStartItem.bridge.provider) ?: templatePresentation.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val launchContext = resolveLaunchContext(e) ?: return
    val quickStartItem = resolveQuickStartItem(buildNewSessionMenuModel()) ?: return
    createNewSession(launchContext.path, quickStartItem.bridge.provider, AgentSessionLaunchMode.STANDARD, launchContext.project)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val launchContext = resolveLaunchContext(e) ?: return emptyArray()
    val menuModel = buildNewSessionMenuModel()
    if (menuModel.standardItems.isEmpty() && menuModel.yoloItems.isEmpty()) {
      return emptyArray()
    }

    val actions = ArrayList<AnAction>(menuModel.standardItems.size + menuModel.yoloItems.size + 2)
    menuModel.standardItems.forEach { item ->
      actions += AgentSessionsTreePopupCreateSessionAction(
        path = launchContext.path,
        item = item,
        project = launchContext.project,
        createNewSession = createNewSession,
      )
    }
    if (menuModel.yoloItems.isNotEmpty()) {
      if (menuModel.standardItems.isNotEmpty()) {
        actions += Separator.getInstance()
      }
      actions += Separator.create(AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"))
      menuModel.yoloItems.forEach { item ->
        actions += AgentSessionsTreePopupCreateSessionAction(
          path = launchContext.path,
          item = item,
          project = launchContext.project,
          createNewSession = createNewSession,
        )
      }
    }
    return actions.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveLaunchContext(event: AnActionEvent?): NewSessionLaunchContext? {
    if (event == null) {
      return null
    }

    val treeContext = resolveContext(event)
    if (treeContext != null) {
      val path = newSessionPathFromNode(treeContext) ?: return null
      return NewSessionLaunchContext(path = path, project = treeContext.project)
    }

    val editorContext = resolveEditorContext(event) ?: return null
    return NewSessionLaunchContext(path = editorContext.path, project = editorContext.project)
  }

  private fun buildNewSessionMenuModel(): TreePopupNewSessionMenuModel {
    val bridges = allBridges()
    val standardItems = ArrayList<TreePopupNewSessionMenuItem>(bridges.size)
    val yoloItems = ArrayList<TreePopupNewSessionMenuItem>()

    bridges.forEach { bridge ->
      if (AgentSessionLaunchMode.STANDARD in bridge.supportedLaunchModes) {
        standardItems += TreePopupNewSessionMenuItem(
          bridge = bridge,
          mode = AgentSessionLaunchMode.STANDARD,
          label = AgentSessionsBundle.message(bridge.newSessionLabelKey),
          isEnabled = true,
        )
      }

      val yoloLabelKey = bridge.yoloSessionLabelKey
      if (yoloLabelKey != null && AgentSessionLaunchMode.YOLO in bridge.supportedLaunchModes) {
        yoloItems += TreePopupNewSessionMenuItem(
          bridge = bridge,
          mode = AgentSessionLaunchMode.YOLO,
          label = AgentSessionsBundle.message(yoloLabelKey),
          isEnabled = true,
        )
      }
    }

    return TreePopupNewSessionMenuModel(
      standardItems = standardItems,
      yoloItems = yoloItems,
    )
  }

  private fun resolveQuickStartItem(menuModel: TreePopupNewSessionMenuModel): TreePopupNewSessionMenuItem? {
    val standardItems = menuModel.standardItems
    if (standardItems.isEmpty()) {
      return null
    }

    val preferredProvider = lastUsedProvider()
    if (preferredProvider != null) {
      val preferredItem = standardItems.firstOrNull { it.bridge.provider == preferredProvider && it.isEnabled }
      if (preferredItem != null) {
        return preferredItem
      }
    }

    return standardItems.firstOrNull { it.isEnabled }
  }
}

private class AgentSessionsTreePopupCreateSessionAction(
  private val path: String,
  private val item: TreePopupNewSessionMenuItem,
  private val project: Project,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit,
) : DumbAwareAction(item.label, null, providerIcon(item.bridge.provider)) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = item.isEnabled
    e.presentation.description = if (item.isEnabled) {
      null
    }
    else {
      AgentSessionsBundle.message(
        "toolwindow.action.new.session.unavailable",
        providerDisplayName(item.bridge.provider),
      )
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!item.isEnabled) return
    createNewSession(path, item.bridge.provider, item.mode, project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun newSessionPathFromNode(context: AgentSessionsTreePopupActionContext): String? {
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

private data class TreePopupNewSessionMenuModel(
  val standardItems: List<TreePopupNewSessionMenuItem>,
  val yoloItems: List<TreePopupNewSessionMenuItem>,
)

private data class NewSessionLaunchContext(
  val path: String,
  val project: Project,
)

private data class TreePopupNewSessionMenuItem(
  val bridge: AgentSessionProviderBridge,
  val mode: AgentSessionLaunchMode,
  val label: @Nls String,
  val isEnabled: Boolean,
)
