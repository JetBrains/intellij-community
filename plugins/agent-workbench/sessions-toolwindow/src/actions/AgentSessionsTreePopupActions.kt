// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.actions.buildNewThreadActionModel
import com.intellij.agent.workbench.sessions.actions.buildNewThreadMenuActions
import com.intellij.agent.workbench.sessions.actions.createNewThreadViaService
import com.intellij.agent.workbench.sessions.actions.launchQuickStartThread
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.toolwindow.ui.providerIcon
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
import javax.swing.Icon

internal object AgentSessionsTreePopupDataKeys {
  @JvmField
  val CONTEXT: DataKey<AgentSessionsTreePopupActionContext> =
    DataKey.create("agent.workbench.sessions.tree.popup.context")
}

internal data class AgentSessionsTreePopupActionContext(
  @JvmField val project: Project,
  val target: SessionActionTarget,
  @JvmField val archiveTargets: List<ArchiveThreadTarget>,
)

internal fun resolveAgentSessionsTreePopupActionContext(event: AnActionEvent): AgentSessionsTreePopupActionContext? {
  return event.getData(AgentSessionsTreePopupDataKeys.CONTEXT)
}

internal class AgentSessionsTreePopupOpenAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val isDedicatedProject: (Project) -> Boolean
  private val openProject: (String, AgentWorkbenchEntryPoint) -> Unit
  private val openThread: (String, AgentSessionThread, Project, AgentWorkbenchEntryPoint) -> Unit
  private val openSubAgent: (String, AgentSessionThread, AgentSubAgent, Project, AgentWorkbenchEntryPoint) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject
    openProject = { path, entryPoint -> service<AgentSessionLaunchService>().openOrFocusProject(path, entryPoint) }
    openThread = { path, thread, project, entryPoint -> service<AgentSessionLaunchService>().openChatThread(path, thread, entryPoint, project) }
    openSubAgent = { path, thread, subAgent, project, entryPoint ->
      service<AgentSessionLaunchService>().openChatSubAgent(path, thread, subAgent, entryPoint, project)
    }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
    openProject: (String, AgentWorkbenchEntryPoint) -> Unit,
    openThread: (String, AgentSessionThread, Project, AgentWorkbenchEntryPoint) -> Unit,
    openSubAgent: (String, AgentSessionThread, AgentSubAgent, Project, AgentWorkbenchEntryPoint) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.isDedicatedProject = isDedicatedProject
    this.openProject = openProject
    this.openThread = openThread
    this.openSubAgent = openSubAgent
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val dedicatedFrame = context?.let { isDedicatedProject(it.project) } == true
    val canOpen = when (val target = context?.target) {
      is SessionActionTarget.Project -> !target.isOpen || dedicatedFrame
      is SessionActionTarget.Worktree,
      is SessionActionTarget.Thread,
      is SessionActionTarget.SubAgent -> true
      else -> false
    }
    e.presentation.isEnabledAndVisible = canOpen
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (val target = context.target) {
      is SessionActionTarget.Project -> openProject(target.path, AgentWorkbenchEntryPoint.TREE_POPUP)
      is SessionActionTarget.Worktree -> openProject(target.path, AgentWorkbenchEntryPoint.TREE_POPUP)
      is SessionActionTarget.Thread -> {
        val thread = target.thread ?: return
        openThread(target.path, thread, context.project, AgentWorkbenchEntryPoint.TREE_POPUP)
      }
      is SessionActionTarget.SubAgent -> {
        val thread = target.thread ?: return
        val subAgent = target.subAgent ?: return
        openSubAgent(target.path, thread, subAgent, context.project, AgentWorkbenchEntryPoint.TREE_POPUP)
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
    showMoreProjects = { service<AgentSessionsStateStore>().showMoreProjects() }
    showMoreThreads = { path -> service<AgentSessionsStateStore>().showMoreThreads(path) }
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
    val target = context?.target
    if (target is SessionActionTarget.MoreProjects || target is SessionActionTarget.MoreThreads) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = morePopupLabel(target)
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (val target = context.target) {
      is SessionActionTarget.MoreProjects -> showMoreProjects()
      is SessionActionTarget.MoreThreads -> {
        showMoreThreads(target.path)
      }
      else -> Unit
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupNewThreadGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext,
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
) : ActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val path = context?.target?.let(::newThreadPathFromTarget)
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode())
    if (path == null || !actionModel.menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = actionModel.quickStartItem != null
    e.presentation.icon = actionModel.quickStartItem?.let { quickStartProviderIcon(it.bridge.provider, it.mode) } ?: templatePresentation.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val path = newThreadPathFromTarget(context.target) ?: return
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode())
    launchQuickStartThread(path, context.project, actionModel.quickStartItem, AgentWorkbenchEntryPoint.TREE_POPUP, createNewSession)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val context = e?.let(resolveContext) ?: return emptyArray()
    val path = newThreadPathFromTarget(context.target) ?: return emptyArray()
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode())
    return buildNewThreadMenuActions(
      path = path,
      project = context.project,
      menuModel = actionModel.menuModel,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      createNewSession = createNewSession,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun newThreadPathFromTarget(target: SessionActionTarget): String? {
  return when (target) {
    is SessionActionTarget.Project -> target.path
    is SessionActionTarget.Worktree -> target.path
    else -> null
  }
}

private fun quickStartProviderIcon(provider: AgentSessionProvider, mode: AgentSessionLaunchMode): Icon? {
  val icon = providerIcon(provider) ?: return null
  if (mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}

private fun morePopupLabel(target: SessionActionTarget): @Nls String {
  return when (target) {
    is SessionActionTarget.MoreProjects -> AgentSessionsBundle.message("toolwindow.action.more.count", target.hiddenCount)
    is SessionActionTarget.MoreThreads ->
      target.hiddenCount?.let { AgentSessionsBundle.message("toolwindow.action.more.count", it) }
      ?: AgentSessionsBundle.message("toolwindow.action.more")
    else -> AgentSessionsBundle.message("toolwindow.action.more")
  }
}
