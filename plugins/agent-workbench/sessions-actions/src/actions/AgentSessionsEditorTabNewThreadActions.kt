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
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.agent.workbench.sessions.service.selectedChatSourceProjectPath
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.JComponent

internal class AgentSessionsEditorTabNewThreadQuickAction @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? =
    { event -> resolveAgentSessionsEditorTabNewThreadContext(event) },
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
  private val showProjectPopup: (List<AgentPromptProjectPathCandidate>, AnActionEvent, (AgentPromptProjectPathCandidate) -> Unit) -> Unit =
    ::showQuickStartProjectPopup,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val quickStartItem = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode()).quickStartItem
    if (quickStartItem == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.icon = providerIconWithMode(quickStartItem.bridge.provider, quickStartItem.mode)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val quickStartItem = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode()).quickStartItem ?: return
    when (val target = context.target ?: return) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> {
        launchQuickStartThread(
          path = target.path,
          project = context.project,
          quickStartItem = quickStartItem,
          entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK,
          createNewSession = createNewSession,
        )
      }
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> {
        showProjectPopup(target.candidates, e) { selected ->
          launchQuickStartThread(
            path = selected.path,
            project = context.project,
            quickStartItem = quickStartItem,
            entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK,
            createNewSession = createNewSession,
          )
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsEditorTabNewThreadPopupGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? =
    { event -> resolveAgentSessionsEditorTabNewThreadContext(event) },
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
) : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    if (resolveContext(e) == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val menuModel = buildNewThreadMenuModel(allBridges())
    if (!menuModel.hasEntries()) {
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
    val target = context.target ?: return emptyArray()
    val menuModel = buildNewThreadMenuModel(allBridges())
    return when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> buildNewThreadMenuActions(
        path = target.path,
        project = context.project,
        menuModel = menuModel,
        entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
        createNewSession = createNewSession,
      )
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> target.candidates.map { candidate ->
        buildProjectCandidatePopupGroup(
          candidate = candidate,
          project = context.project,
          menuModel = menuModel,
          entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
          createNewSession = createNewSession,
        )
      }.toTypedArray()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsEditorTabNewThreadContext(
  val project: Project,
  private val resolveTarget: () -> AgentSessionsEditorTabNewThreadTarget?,
) {
  val target: AgentSessionsEditorTabNewThreadTarget?
    get() = resolveTarget()
}

internal sealed class AgentSessionsEditorTabNewThreadTarget {
  data class Direct(val path: String) : AgentSessionsEditorTabNewThreadTarget()
  data class Candidates(val candidates: List<AgentPromptProjectPathCandidate>) : AgentSessionsEditorTabNewThreadTarget()
}

internal fun resolveAgentSessionsEditorTabNewThreadContext(
  event: AnActionEvent,
  isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  openInDedicatedFrame: () -> Boolean = ::openChatInDedicatedFrame,
  openProjectPaths: () -> List<String> = ::collectOpenAgentSessionProjectPaths,
  resolveChatContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
): AgentSessionsEditorTabNewThreadContext? {
  val project = event.project ?: return null
  val chatContext = resolveChatContext(event)
  return when {
    isDedicatedProject(project) -> AgentSessionsEditorTabNewThreadContext(project) {
      resolveDedicatedFrameNewThreadTarget(chatContext, openProjectPaths)
    }
    openInDedicatedFrame() -> null
    else -> {
      val target = resolveProjectFrameNewThreadTarget(project, chatContext) ?: return null
      AgentSessionsEditorTabNewThreadContext(project) { target }
    }
  }
}

internal fun resolveAgentSessionsMainToolbarNewThreadContext(
  event: AnActionEvent,
  isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  openProjectPaths: () -> List<String> = ::collectOpenAgentSessionProjectPaths,
  resolveChatContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
  selectedSourcePath: (Project) -> String? = ::selectedChatSourceProjectPath,
): AgentSessionsEditorTabNewThreadContext? {
  val project = event.project ?: return null
  val chatContext = resolveChatContext(event)
  return if (isDedicatedProject(project)) {
    AgentSessionsEditorTabNewThreadContext(project) {
      resolveDedicatedFrameNewThreadTarget(chatContext, openProjectPaths)
    }
  }
  else {
    val target = resolveMainToolbarProjectFrameNewThreadTarget(project, chatContext, selectedSourcePath) ?: return null
    AgentSessionsEditorTabNewThreadContext(project) { target }
  }
}

private fun resolveDedicatedFrameNewThreadTarget(
  chatContext: AgentChatEditorTabActionContext?,
  openProjectPaths: () -> List<String>,
): AgentSessionsEditorTabNewThreadTarget? {
  // Source-project candidates are resolved lazily on click/popup open. In a multi-project dedicated frame,
  // require an explicit choice instead of silently using the active Agent tab's source path.
  val candidates = buildAgentSessionProjectPathCandidates(openProjectPaths())
  return when (candidates.size) {
    0 -> chatContext?.let { AgentSessionsEditorTabNewThreadTarget.Direct(it.path) }
    1 -> AgentSessionsEditorTabNewThreadTarget.Direct(candidates.single().path)
    else -> AgentSessionsEditorTabNewThreadTarget.Candidates(candidates)
  }
}

private fun resolveProjectFrameNewThreadTarget(
  project: Project,
  chatContext: AgentChatEditorTabActionContext?,
): AgentSessionsEditorTabNewThreadTarget? {
  val path = chatContext?.path ?: normalizeOpenableSourceProjectPath(project.basePath) ?: return null
  return AgentSessionsEditorTabNewThreadTarget.Direct(path)
}

private fun resolveMainToolbarProjectFrameNewThreadTarget(
  project: Project,
  chatContext: AgentChatEditorTabActionContext?,
  selectedSourcePath: (Project) -> String?,
): AgentSessionsEditorTabNewThreadTarget? {
  val path = chatContext?.path
             ?: normalizeOpenableSourceProjectPath(selectedSourcePath(project))
             ?: normalizeOpenableSourceProjectPath(project.basePath)
             ?: return null
  return AgentSessionsEditorTabNewThreadTarget.Direct(path)
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

internal fun showQuickStartProjectPopup(
  candidates: List<AgentPromptProjectPathCandidate>,
  e: AnActionEvent,
  onResolved: (AgentPromptProjectPathCandidate) -> Unit,
) {
  val group = buildQuickStartProjectPopupGroup(candidates, onResolved)
  showActionGroupPopup(group, e)
}

private fun showActionGroupPopup(group: ActionGroup, e: AnActionEvent) {
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

private fun openChatInDedicatedFrame(): Boolean {
  return AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)
}

private const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID = "agent.workbench.chat.open.in.dedicated.frame"
