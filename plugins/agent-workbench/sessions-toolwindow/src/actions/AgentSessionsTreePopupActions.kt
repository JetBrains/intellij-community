// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.AgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuActions
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuModel
import com.intellij.agent.workbench.sessions.launchQuickStartProfile
import com.intellij.agent.workbench.sessions.actions.createNewThreadViaService
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.providerItemIconWithMode
import com.intellij.agent.workbench.sessions.resolveAgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.service.AgentArchivedSessionsService
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
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

internal object AgentSessionsTreePopupDataKeys {
  @JvmField
  val CONTEXT: DataKey<AgentSessionsTreePopupActionContext> =
    DataKey.create("agent.workbench.sessions.tree.popup.context")
}

internal data class AgentSessionsTreePopupActionContext(
  @JvmField val project: Project,
  val target: SessionActionTarget,
  @JvmField val archiveTargets: List<ArchiveThreadTarget>,
  @JvmField val unarchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  @JvmField val selectedThreadTargets: List<SessionActionTarget.Thread> = emptyList(),
  @JvmField val taskFolderArchiveTargets: List<ArchiveThreadTarget> = emptyList(),
  @JvmField val newThreadActionAvailable: Boolean = true,
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
    openThread =
      { path, thread, project, entryPoint -> service<AgentSessionLaunchService>().openChatThread(path, thread, entryPoint, project) }
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
      is SessionActionTarget.SubAgent,
        -> true
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
    showMoreProjects = {
      if (service<AgentSessionThreadViewStateService>().state.value.mode == AgentSessionThreadViewMode.ARCHIVED) {
        service<AgentArchivedSessionsService>().showMoreProjects()
      }
      else {
        service<AgentSessionsStateStore>().showMoreProjects()
      }
    }
    showMoreThreads = { path ->
      if (service<AgentSessionThreadViewStateService>().state.value.mode == AgentSessionThreadViewMode.ARCHIVED) {
        service<AgentArchivedSessionsService>().showMoreThreads(path)
      }
      else {
        service<AgentSessionsStateStore>().showMoreThreads(path)
      }
    }
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
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles() },
  private val defaultLaunchProfileId: () -> String? = { service<AgentSessionUiPreferencesStateService>().getDefaultLaunchProfileId() },
) : ActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val menu = resolveNewThreadMenu(e)
    if (menu == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val quickStartItem = menu.selection.quickStartItem
    if (quickStartItem == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = quickStartItem.menuItem.isEnabled
    e.presentation.icon = providerItemIconWithMode(quickStartItem.menuItem)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val menu = resolveNewThreadMenu(e) ?: return
    launchQuickStartProfile(
      path = menu.path,
      project = menu.context.project,
      quickStartItem = menu.selection.quickStartItem,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      createNewSession = createNewSession,
    )
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val menu = e?.let(::resolveNewThreadMenu) ?: return emptyArray()
    return buildAgentSessionLaunchProfileMenuActions(
      path = menu.path,
      project = menu.context.project,
      selection = menu.selection,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      createNewSession = createNewSession,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveNewThreadMenu(e: AnActionEvent): NewThreadMenu? {
    val context = resolveContext(e) ?: return null
    if (!context.newThreadActionAvailable) return null
    val path = newThreadPathFromTarget(context.target) ?: return null
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), context.project)
    if (!menuModel.hasEntries()) return null
    val selection = resolveAgentSessionLaunchProfileSelection(menuModel, userLaunchProfiles(), defaultLaunchProfileId())
    if (selection.profiles.isEmpty()) return null
    return NewThreadMenu(context, path, selection)
  }

  private data class NewThreadMenu(
    val context: AgentSessionsTreePopupActionContext,
    val path: String,
    val selection: AgentSessionLaunchProfileSelection,
  )
}

private fun newThreadPathFromTarget(target: SessionActionTarget): String? {
  return when (target) {
    is SessionActionTarget.Project -> target.path
    is SessionActionTarget.Worktree -> target.path
    else -> null
  }
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
