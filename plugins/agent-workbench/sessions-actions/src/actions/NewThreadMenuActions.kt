// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.buildAgentSessionProviderMenuActions
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchProfileSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.generationSettingsForPlanMode
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.providers.initialMessageRequestForLaunchProfile
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.setProviderItemLaunchProfileActiveMarker
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.ui.AgentWorkbenchPopupRow
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.LafIconLookup
import org.jetbrains.annotations.Nls

fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(path, provider, mode, entryPoint, currentProject)
}

fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return
  service<AgentSessionLaunchService>().createNewSession(
    path = path,
    provider = provider,
    mode = profile.launchMode,
    entryPoint = entryPoint,
    currentProject = currentProject,
    initialMessageRequest = initialMessageRequestForLaunchProfile(profile),
    generationSettings = generationSettingsForPlanMode(
      generationSettings = profile.generationSettings,
      startInPlanMode = false,
    ),
  )
}

fun buildNewThreadMenuModel(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): AgentSessionProviderMenuModel {
  val enabledBridges = service<AgentSessionProviderSettingsService>().enabledProviders(bridges)
  return buildAgentSessionProviderMenuModel(enabledBridges, providerAvailabilitySnapshot(enabledBridges, project))
}

/**
 * Synchronous action updates and tree renderers cannot call [AgentSessionProviderDescriptor.isCliAvailable]
 * directly. They read the project-level availability cache instead and request a background refresh when
 * the cache has not been populated yet. Prominent providers are treated as enabled so first paint does not
 * disable every provider while startup prewarm is still running; discoverable providers stay hidden until
 * a background probe resolves them as available.
 */
fun providerAvailabilitySnapshot(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): Map<AgentSessionProvider, Boolean> {
  val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
  availabilityService.requestRefresh(bridges)
  return availabilityService.availabilitySnapshot(bridges)
}

internal fun quickStartActionText(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.bridge.quickStartActionTextKey, quickStartLabel(item))
}

internal fun quickStartActionDescription(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.bridge.quickStartActionDescriptionKey, quickStartLabel(item))
}

internal fun quickStartLabel(item: AgentSessionProviderMenuItem): @Nls String {
  val labelKey = if (item.mode == AgentSessionLaunchMode.STANDARD) item.bridge.quickStartLabelKey else item.labelKey
  return AgentSessionsBundle.message(labelKey)
}

fun launchQuickStartThread(
  path: String,
  project: Project,
  quickStartItem: AgentSessionProviderMenuItem?,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) {
  val item = quickStartItem ?: return
  createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
}

fun buildNewThreadMenuActions(
  path: String,
  project: Project,
  menuModel: AgentSessionProviderMenuModel,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
): Array<AnAction> {
  if (!menuModel.hasEntries()) {
    return emptyArray()
  }

  return buildAgentSessionProviderMenuActions(menuModel) { item ->
    createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
  }
}

data class NewThreadProfileMenuItem(
  @JvmField val profile: AgentPromptLaunchProfile,
  @JvmField val menuItem: AgentSessionProviderMenuItem,
)

fun launchQuickStartThread(
  path: String,
  project: Project,
  quickStartItem: NewThreadProfileMenuItem?,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
) {
  val item = quickStartItem?.takeIf { profileItem -> profileItem.menuItem.isEnabled } ?: return
  createNewSession(path, item.profile, project, entryPoint)
}

fun resolveNewThreadProfileItem(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
  userProfiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
  menuModel: AgentSessionProviderMenuModel = buildNewThreadMenuModel(bridges, project),
): NewThreadProfileMenuItem? {
  val profileItems = resolveNewThreadProfileItems(menuModel, userProfiles, activeProfileId)
  return resolveNewThreadProfileItem(profileItems, activeProfileId)
}

fun resolveNewThreadProfileItem(
  menuModel: AgentSessionProviderMenuModel,
  userProfiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
): NewThreadProfileMenuItem? {
  val profileItems = resolveNewThreadProfileItems(menuModel, userProfiles, activeProfileId)
  return resolveNewThreadProfileItem(profileItems, activeProfileId)
}

fun resolveNewThreadProfileItem(
  profileItems: List<NewThreadProfileMenuItem>,
  activeProfileId: String?,
): NewThreadProfileMenuItem? {
  return profileItems.firstOrNull { item ->
    activeProfileId == null || item.profile.id == activeProfileId
  } ?: profileItems.firstOrNull()
}

fun resolveNewThreadProfileItems(
  menuModel: AgentSessionProviderMenuModel,
  userProfiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
): List<NewThreadProfileMenuItem> {
  val snapshot = AgentSessionLaunchProfileSnapshot(
    builtInProfiles = buildBuiltInLaunchProfiles(menuModel, ::quickStartLabel),
    userProfiles = userProfiles,
    activeProfileId = activeProfileId,
  )
  val items = (menuModel.standardItems + menuModel.yoloItems)
  return snapshot.allProfiles.mapNotNull { profile ->
    val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return@mapNotNull null
    val menuItem = items.firstOrNull { item -> item.bridge.provider == provider && item.mode == profile.launchMode }
                   ?: return@mapNotNull null
    NewThreadProfileMenuItem(profile, menuItem)
  }
}

fun buildNewThreadProfileMenuActions(
  path: String,
  project: Project,
  profiles: List<NewThreadProfileMenuItem>,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  activeLaunchProfileId: String?,
  includeManageAction: Boolean = true,
): Array<AnAction> {
  val actions = mutableListOf<AnAction>()
  forEachNewThreadProfileSection(profiles) { title, sectionProfiles ->
    if (sectionProfiles.isNotEmpty()) {
      if (actions.isNotEmpty()) {
        actions.add(Separator.getInstance())
      }
      if (title != null) {
        actions.add(Separator.create(title))
      }
      sectionProfiles.forEach { profileItem ->
        actions.add(NewThreadProfileLaunchAction(
          path = path,
          project = project,
          profileItem = profileItem,
          entryPoint = entryPoint,
          createNewSession = createNewSession,
          activeLaunchProfileId = activeLaunchProfileId,
        ))
      }
    }
  }
  if (includeManageAction) {
    appendManageLaunchProfilesAction(actions)
  }
  return actions.toTypedArray()
}

fun buildNewThreadProfileMenuRows(
  path: String,
  project: Project,
  profiles: List<NewThreadProfileMenuItem>,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  activeLaunchProfileId: String?,
  includeManageAction: Boolean = true,
  event: AnActionEvent,
): List<AgentWorkbenchPopupRow> {
  val rows = mutableListOf<AgentWorkbenchPopupRow>()
  forEachNewThreadProfileSection(profiles) { title, sectionProfiles ->
    if (sectionProfiles.isNotEmpty()) {
      sectionProfiles.forEachIndexed { index, profileItem ->
        rows.add(createNewThreadProfileMenuRow(
          path = path,
          project = project,
          profileItem = profileItem,
          entryPoint = entryPoint,
          createNewSession = createNewSession,
          activeLaunchProfileId = activeLaunchProfileId,
          separatorText = if (index == 0) title else null,
        ))
      }
    }
  }
  if (includeManageAction) {
    appendManageLaunchProfilesRow(rows, event)
  }
  return rows
}

private fun forEachNewThreadProfileSection(
  profiles: List<NewThreadProfileMenuItem>,
  handleSection: (@Nls String?, List<NewThreadProfileMenuItem>) -> Unit,
) {
  val standardProfiles = profiles.filter { profileItem -> profileItem.profile.launchMode != AgentSessionLaunchMode.YOLO }
  val yoloProfiles = profiles.filter { profileItem -> profileItem.profile.launchMode == AgentSessionLaunchMode.YOLO }
  handleSection(null, standardProfiles)
  handleSection(AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"), yoloProfiles)
}

private fun createNewThreadProfileMenuRow(
  path: String,
  project: Project,
  profileItem: NewThreadProfileMenuItem,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  activeLaunchProfileId: String?,
  separatorText: @Nls String?,
): AgentWorkbenchPopupRow {
  val isActiveProfile = profileItem.profile.id == activeLaunchProfileId
  val isEnabled = profileItem.menuItem.isEnabled
  return AgentWorkbenchPopupRow(
    text = profileItem.profile.name,
    separatorText = separatorText,
    primaryIcon = providerItemMonochromeIconWithMode(profileItem.menuItem),
    secondaryIcon = when {
      !isActiveProfile -> null
      isEnabled -> LafIconLookup.getIcon("checkmark")
      else -> LafIconLookup.getDisabledIcon("checkmark")
    },
    tooltipText = newThreadProfileActionDescription(
      profileItem = profileItem,
      projectLabel = projectLabelForPath(path),
    ),
    selected = isActiveProfile,
    selectable = isEnabled,
    onChosen = {
      if (isEnabled) {
        createNewSession(path, profileItem.profile, project, entryPoint)
      }
    },
  )
}

fun appendManageLaunchProfilesAction(actions: MutableList<AnAction>): MutableList<AnAction> {
  val manageAction = ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Prompt.MANAGE_LAUNCH_PROFILES) ?: return actions
  if (actions.isNotEmpty()) {
    actions.add(Separator.getInstance())
  }
  actions.add(manageAction)
  return actions
}

fun appendManageLaunchProfilesRow(rows: MutableList<AgentWorkbenchPopupRow>, event: AnActionEvent): List<AgentWorkbenchPopupRow> {
  val manageAction = ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Prompt.MANAGE_LAUNCH_PROFILES) ?: return rows
  val text = manageAction.templatePresentation.text ?: return rows
  rows.add(AgentWorkbenchPopupRow(
    text = text,
    separatorText = "",
    tooltipText = manageAction.templatePresentation.description,
    onChosen = {
      val actionEvent = AnActionEvent.createEvent(manageAction, event.dataContext, null, event.place, ActionUiKind.POPUP, event.inputEvent)
      ActionUtil.performAction(manageAction, actionEvent)
    },
  ))
  return rows
}

fun newThreadProfileActionText(item: NewThreadProfileMenuItem): @Nls String {
  if (item.profile.kind == AgentPromptLaunchProfileKind.BUILT_IN) {
    return quickStartActionText(item.menuItem)
  }
  return AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewThreadProfileQuick.text", item.profile.name)
}

fun newThreadProfileActionDescription(
  profileItem: NewThreadProfileMenuItem,
  projectLabel: @Nls String,
): @Nls String {
  if (!profileItem.menuItem.isEnabled) {
    return disabledProviderReason(profileItem.menuItem)
  }
  return AgentSessionsBundle.message(
    "action.AgentWorkbenchSessions.MainToolbar.NewThread.profile.description",
    profileItem.profile.name,
    quickStartLabel(profileItem.menuItem),
    projectLabel,
  )
}

private class NewThreadProfileLaunchAction(
  private val path: String,
  private val project: Project,
  private val profileItem: NewThreadProfileMenuItem,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val activeLaunchProfileId: String?,
) : DumbAwareAction(profileItem.profile.name, null, providerItemMonochromeIconWithMode(profileItem.menuItem)) {
  init {
    setProviderItemLaunchProfileActiveMarker(templatePresentation, profileItem.menuItem, isActiveProfile())
    templatePresentation.description = newThreadProfileActionDescription(
      profileItem = profileItem,
      projectLabel = projectLabelForPath(path),
    )
  }

  private fun isActiveProfile(): Boolean {
    return profileItem.profile.id == activeLaunchProfileId
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = profileItem.menuItem.isEnabled
    setProviderItemLaunchProfileActiveMarker(e.presentation, profileItem.menuItem, isActiveProfile())
    e.presentation.description = newThreadProfileActionDescription(
      profileItem = profileItem,
      projectLabel = projectLabelForPath(path),
    )
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!profileItem.menuItem.isEnabled) return
    createNewSession(path, profileItem.profile, project, entryPoint)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun disabledProviderReason(item: AgentSessionProviderMenuItem): @Nls String {
  val reasonKey = item.disabledReasonKey
  if (reasonKey != null) {
    return AgentSessionsBundle.message(reasonKey)
  }
  return AgentSessionsBundle.message(
    "toolwindow.action.new.session.unavailable",
    AgentSessionsBundle.message(item.bridge.displayNameKey),
  )
}

fun projectLabelForPath(path: String): @NlsSafe String {
  val trimmed = path.trimEnd('/')
  return trimmed.substringAfterLast('/').ifEmpty { trimmed }
}
