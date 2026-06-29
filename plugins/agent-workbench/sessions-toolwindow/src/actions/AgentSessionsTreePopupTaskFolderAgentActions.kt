// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.AgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuActions
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuModel
import com.intellij.agent.workbench.sessions.launchQuickStartProfile
import com.intellij.agent.workbench.sessions.providerItemIconWithMode
import com.intellij.agent.workbench.sessions.resolveAgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.service.AgentPreparedNewSessionLaunchContext
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderService
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.builtInLaunchProfileId
import com.intellij.platform.ai.agent.sessions.core.providers.hasEntries

internal class AgentSessionsTreePopupTaskFolderAgentGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext,
  private val allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles() },
  private val taskFolderAgentLaunchProfileId: () -> String? = {
    service<AgentSessionUiPreferencesStateService>().getTaskFolderAgentLaunchProfileId()
  },
  private val setTaskFolderAgentLaunchProfileId: (String?) -> Unit = { profileId ->
    service<AgentSessionUiPreferencesStateService>().setTaskFolderAgentLaunchProfileId(profileId)
  },
  private val createTaskFolderAgent: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit =
    { path, profile, project, entryPoint -> createTaskFolderAgentViaService(path, profile, project, entryPoint, folder = null) },
) : ActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val menu = resolveTaskFolderAgentMenu(e)
    if (menu == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val quickStartItem = menu.selection.quickStartItem
    val enabled = quickStartItem?.menuItem?.isEnabled == true
    e.presentation.isVisible = true
    e.presentation.isEnabled = enabled
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = enabled
    e.presentation.icon = quickStartItem?.let { providerItemIconWithMode(it.menuItem) }
    if (!enabled) {
      e.presentation.description = AgentSessionsBundle.message("action.AgentWorkbenchSessions.TreePopup.TaskFolderAgent.disabled.description")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val menu = resolveTaskFolderAgentMenu(e) ?: return
    launchQuickStartProfile(
      path = menu.path,
      project = menu.context.project,
      quickStartItem = menu.selection.quickStartItem,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      createNewSession = ::launchTaskFolderAgent,
    )
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val menu = e?.let(::resolveTaskFolderAgentMenu) ?: return emptyArray()
    if (menu.selection.profiles.isEmpty()) return emptyArray()
    return buildAgentSessionLaunchProfileMenuActions(
      path = menu.path,
      project = menu.context.project,
      selection = menu.selection,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      createNewSession = ::launchTaskFolderAgent,
      includeManageAction = false,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveTaskFolderAgentMenu(e: AnActionEvent): TaskFolderAgentMenu? {
    val context = resolveContext(e) ?: return null
    val path = taskFolderAgentPathFromTarget(context.target) ?: return null
    val selection = resolveTaskFolderAgentLaunchProfileSelection(
      project = context.project,
      allBridges = allBridges(),
      userLaunchProfiles = userLaunchProfiles(),
      preferredProfileId = taskFolderAgentLaunchProfileId(),
    )
    return TaskFolderAgentMenu(context = context, path = path, selection = selection)
  }

  private fun launchTaskFolderAgent(
    path: String,
    profile: AgentPromptLaunchProfile,
    project: Project,
    entryPoint: AgentWorkbenchEntryPoint,
  ) {
    setTaskFolderAgentLaunchProfileId(profile.id)
    createTaskFolderAgent(path, profile, project, entryPoint)
  }

  private data class TaskFolderAgentMenu(
    val context: AgentSessionsTreePopupActionContext,
    val path: String,
    val selection: AgentSessionLaunchProfileSelection,
  )
}

internal fun resolveTaskFolderAgentQuickStartProfile(project: Project): AgentPromptLaunchProfile? {
  val selection = resolveTaskFolderAgentLaunchProfileSelection(
    project = project,
    allBridges = AgentSessionProviders.allProviders(),
    userLaunchProfiles = service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles(),
    preferredProfileId = service<AgentSessionUiPreferencesStateService>().getTaskFolderAgentLaunchProfileId(),
  )
  return selection.quickStartItem
    ?.takeIf { item -> item.menuItem.isEnabled }
    ?.profile
}

internal fun createTaskFolderAgentViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  project: Project,
  entryPoint: AgentWorkbenchEntryPoint,
  folder: AgentTaskFolder?,
) {
  service<AgentSessionUiPreferencesStateService>().setTaskFolderAgentLaunchProfileId(profile.id)
  service<AgentSessionLaunchService>().createNewSession(
    path = path,
    launchProfileId = profile.id,
    entryPoint = entryPoint,
    currentProject = project,
    initialMessageRequestBuilder = { context ->
      AgentPromptInitialMessageRequest(prompt = buildTaskFolderAgentPrompt(path = path, folder = folder, context = context))
    },
    preparedLaunchHandler = folder?.let { taskFolder ->
      { context ->
        service<AgentTaskFolderService>().assignThread(path, context.provider, context.threadId, taskFolder.id)
      }
    },
    singleFlightDiscriminator = "task-folder-agent:${folder?.id ?: "new"}",
    updateGeneralProviderPreferences = false,
    threadTitle = folder?.name,
  )
}

private fun resolveTaskFolderAgentLaunchProfileSelection(
  project: Project,
  allBridges: List<AgentSessionProviderDescriptor>,
  userLaunchProfiles: List<AgentPromptLaunchProfile>,
  preferredProfileId: String?,
): AgentSessionLaunchProfileSelection {
  val piBridges = allBridges.filter { descriptor -> descriptor.provider == TASK_FOLDER_AGENT_PROVIDER }
  val menuModel = buildAgentSessionLaunchProfileMenuModel(piBridges, project)
  if (!menuModel.hasEntries()) {
    return AgentSessionLaunchProfileSelection(profiles = emptyList(), quickStartItem = null)
  }
  return resolveAgentSessionLaunchProfileSelection(
    menuModel = menuModel,
    userProfiles = userLaunchProfiles.filter { profile -> profile.providerId == TASK_FOLDER_AGENT_PROVIDER.value },
    preferredProfileId = preferredProfileId,
    fallbackProfileIds = listOf(builtInLaunchProfileId(TASK_FOLDER_AGENT_PROVIDER, AgentSessionLaunchMode.STANDARD)),
  )
}

private fun buildTaskFolderAgentPrompt(
  path: String,
  folder: AgentTaskFolder?,
  context: AgentPreparedNewSessionLaunchContext,
): String {
  if (folder != null) {
    return """
      Continue this Agent Workbench task folder.

      Project path: $path
      Task folder name: ${folder.name}
      Task folder id: ${folder.id}
      Current thread id: ${context.threadId}

      The IDE has already assigned this thread to the task folder. If the work needs issue tracker association, use task folder metadata key "issue".
      Ask for any missing task details, then proceed with the task.
    """.trimIndent()
  }

  return """
    Start an Agent Workbench task-folder workflow for this project.

    Project path: $path
    Current thread id: ${context.threadId}

    When you know the task folder name, call the agent_workbench_create_task_folder tool. If the user mentions an issue tracker id, pass it as the issue parameter.
    If the task details are missing, ask one concise follow-up before creating the folder.
  """.trimIndent()
}

private fun taskFolderAgentPathFromTarget(target: SessionActionTarget): String? {
  return when (target) {
    is SessionActionTarget.Project -> target.path
    is SessionActionTarget.Worktree -> target.path
    else -> null
  }
}

private val TASK_FOLDER_AGENT_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("pi")
