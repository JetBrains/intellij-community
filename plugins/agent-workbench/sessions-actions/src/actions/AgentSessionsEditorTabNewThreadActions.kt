// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.providerIconWithMode
import com.intellij.agent.workbench.sessions.service.buildAgentSessionProjectPathCandidates
import com.intellij.agent.workbench.sessions.service.collectOpenAgentSessionProjectPaths
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.JComponent

internal class AgentSessionsEditorTabNewThreadQuickAction @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
  private val projectPathCandidates: (Project, (Project) -> Boolean) -> List<AgentPromptProjectPathCandidate>? = ::collectProjectPathCandidates,
  private val showProjectPopup: (List<AgentPromptProjectPathCandidate>, AnActionEvent, (AgentPromptProjectPathCandidate) -> Unit) -> Unit =
    ::showQuickStartProjectPopup,
) : AgentSessionsEditorTabActionBase(resolveContext) {
  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode())
    val isVisible = context != null && actionModel.quickStartItem != null
    e.presentation.isEnabledAndVisible = isVisible
    e.presentation.icon = actionModel.quickStartItem?.let { providerIconWithMode(it.bridge.provider, it.mode) } ?: templatePresentation.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode())
    val candidates = projectPathCandidates(context.project, isDedicatedProject)
    if (candidates == null) {
      launchQuickStartThread(context.path, context.project, actionModel.quickStartItem, AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK, createNewSession)
      return
    }
    showProjectPopup(candidates, e) { selected ->
      launchQuickStartThread(
        path = selected.path,
        project = context.project,
        quickStartItem = actionModel.quickStartItem,
        entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK,
        createNewSession = createNewSession,
      )
    }
  }
}

internal class AgentSessionsEditorTabNewThreadPopupGroup @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? =
    ::resolveAgentChatEditorTabActionContext,
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
  private val projectPathCandidates: (Project, (Project) -> Boolean) -> List<AgentPromptProjectPathCandidate>? = ::collectProjectPathCandidates,
) : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val menuModel = buildNewThreadMenuModel(allBridges())
    if (context == null || !menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = false
    e.presentation.icon = templatePresentation.icon
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val context = e?.let(resolveContext) ?: return emptyArray()
    val menuModel = buildNewThreadMenuModel(allBridges())
    val candidates = projectPathCandidates(context.project, isDedicatedProject)
    if (candidates == null) {
      return buildNewThreadMenuActions(
        path = context.path,
        project = context.project,
        menuModel = menuModel,
        entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
        createNewSession = createNewSession,
      )
    }
    return candidates.map { candidate ->
      buildProjectCandidatePopupGroup(
        candidate = candidate,
        project = context.project,
        menuModel = menuModel,
        entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
        createNewSession = createNewSession,
      )
    }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal fun buildProjectCandidatePopupGroup(
  candidate: AgentPromptProjectPathCandidate,
  project: Project,
  menuModel: AgentSessionProviderMenuModel,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
): ActionGroup {
  val group = DumbAwareDefaultActionGroup(candidate.displayName, true)
  buildNewThreadMenuActions(
    path = candidate.path,
    project = project,
    menuModel = menuModel,
    entryPoint = entryPoint,
    createNewSession = createNewSession,
  ).forEach(group::add)
  return group
}

internal fun collectProjectPathCandidates(
  project: Project,
  isDedicatedProject: (Project) -> Boolean,
): List<AgentPromptProjectPathCandidate>? {
  return collectProjectPathCandidates(project, isDedicatedProject, ::collectOpenAgentSessionProjectPaths)
}

internal fun collectProjectPathCandidates(
  project: Project,
  isDedicatedProject: (Project) -> Boolean,
  openProjectPaths: () -> List<String>,
): List<AgentPromptProjectPathCandidate>? {
  if (!isDedicatedProject(project)) return null
  val candidates = buildAgentSessionProjectPathCandidates(openProjectPaths())
  return candidates.takeIf { it.size > 1 }
}

private fun showQuickStartProjectPopup(
  candidates: List<AgentPromptProjectPathCandidate>,
  e: AnActionEvent,
  onResolved: (AgentPromptProjectPathCandidate) -> Unit,
) {
  val group = buildQuickStartProjectPopupGroup(candidates, onResolved)
  val popup = JBPopupFactory.getInstance()
    .createActionGroupPopup(
      null,
      group,
      e.dataContext,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true,
      null,
      Int.MAX_VALUE,
    )
  val anchor = resolveQuickStartProjectPopupAnchor(e)
  if (anchor != null) {
    popup.showUnderneathOf(anchor)
  }
  else {
    popup.showInBestPositionFor(e.dataContext)
  }
}

internal fun buildQuickStartProjectPopupGroup(
  candidates: List<AgentPromptProjectPathCandidate>,
  onResolved: (AgentPromptProjectPathCandidate) -> Unit,
): ActionGroup {
  val group = DumbAwareDefaultActionGroup()
  candidates.forEach { candidate ->
    group.add(object : DumbAwareAction(candidate.displayName, candidate.path.takeUnless { it == candidate.displayName }, null) {
      override fun actionPerformed(e: AnActionEvent) {
        onResolved(candidate)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    })
  }
  return group
}

internal fun resolveQuickStartProjectPopupAnchor(e: AnActionEvent): JComponent? {
  return (e.inputEvent?.component as? JComponent)
         ?: (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent)
}
