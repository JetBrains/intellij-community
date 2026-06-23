// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.ui.AgentWorkbenchActionIds
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionLaunchProfileSnapshot
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.platform.ai.agent.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.platform.ai.agent.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
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

data class AgentSessionLaunchProfileMenuItem(
  @JvmField val profile: AgentPromptLaunchProfile,
  @JvmField val menuItem: AgentSessionProviderMenuItem,
)

data class AgentSessionLaunchProfileSelection(
  @JvmField val profiles: List<AgentSessionLaunchProfileMenuItem>,
  @JvmField val quickStartItem: AgentSessionLaunchProfileMenuItem?,
) {
  val checkedLaunchProfileId: String?
    get() = quickStartItem?.profile?.id
}

fun buildAgentSessionLaunchProfileMenuModel(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): AgentSessionProviderMenuModel {
  val providerSettings = service<AgentSessionProviderSettingsService>()
  val enabledBridges = bridges.filter { bridge -> providerSettings.isProviderEnabled(bridge.provider) }
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

fun launchQuickStartProfile(
  path: String,
  project: Project,
  quickStartItem: AgentSessionLaunchProfileMenuItem?,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
) {
  val item = quickStartItem?.takeIf { profileItem -> profileItem.menuItem.isEnabled } ?: return
  createNewSession(path, item.profile, project, entryPoint)
}

fun resolveAgentSessionLaunchProfileSelection(
  menuModel: AgentSessionProviderMenuModel,
  userProfiles: List<AgentPromptLaunchProfile>,
  preferredProfileId: String?,
  fallbackProfileIds: List<String> = emptyList(),
  quickStartItemFilter: (AgentSessionLaunchProfileMenuItem) -> Boolean = { true },
): AgentSessionLaunchProfileSelection {
  val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userProfiles)
  return resolveAgentSessionLaunchProfileSelection(
    profiles = profiles,
    preferredProfileId = preferredProfileId,
    fallbackProfileIds = fallbackProfileIds,
    quickStartItemFilter = quickStartItemFilter,
  )
}

fun resolveAgentSessionLaunchProfileSelection(
  profiles: List<AgentSessionLaunchProfileMenuItem>,
  preferredProfileId: String?,
  fallbackProfileIds: List<String> = emptyList(),
  quickStartItemFilter: (AgentSessionLaunchProfileMenuItem) -> Boolean = { true },
): AgentSessionLaunchProfileSelection {
  return AgentSessionLaunchProfileSelection(
    profiles = profiles,
    quickStartItem = resolveAgentSessionLaunchProfileItem(profiles.filter(quickStartItemFilter), preferredProfileId, fallbackProfileIds),
  )
}

private fun resolveAgentSessionLaunchProfileItem(
  profileItems: List<AgentSessionLaunchProfileMenuItem>,
  preferredProfileId: String?,
  fallbackProfileIds: List<String> = emptyList(),
): AgentSessionLaunchProfileMenuItem? {
  preferredProfileId
    ?.let { profileId -> profileItems.firstOrNull { item -> item.profile.id == profileId } }
    ?.let { return it }

  fallbackProfileIds.forEach { profileId ->
    profileItems.firstOrNull { item -> item.profile.id == profileId }?.let { return it }
  }

  return profileItems.firstOrNull()
}

fun resolveAgentSessionLaunchProfileItems(
  menuModel: AgentSessionProviderMenuModel,
  userProfiles: List<AgentPromptLaunchProfile>,
): List<AgentSessionLaunchProfileMenuItem> {
  val snapshot = AgentSessionLaunchProfileSnapshot(
    builtInProfiles = buildBuiltInLaunchProfiles(menuModel, ::quickStartLabel),
    userProfiles = userProfiles,
  )
  val items = menuModel.standardItems + menuModel.yoloItems
  return snapshot.allProfiles.mapNotNull { profile ->
    val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return@mapNotNull null
    val menuItem = items.firstOrNull { item -> item.bridge.provider == provider && item.mode == profile.launchMode }
                   ?: return@mapNotNull null
    AgentSessionLaunchProfileMenuItem(profile, menuItem)
  }
}

fun buildAgentSessionLaunchProfileMenuActions(
  path: String,
  project: Project,
  selection: AgentSessionLaunchProfileSelection,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  includeManageAction: Boolean = true,
): Array<AnAction> {
  val actions = mutableListOf<AnAction>()
  forEachLaunchProfileSection(selection.profiles) { title, sectionProfiles ->
    if (sectionProfiles.isNotEmpty()) {
      if (actions.isNotEmpty()) {
        actions.add(Separator.getInstance())
      }
      if (title != null) {
        actions.add(Separator.create(title))
      }
      sectionProfiles.forEach { profileItem ->
        actions.add(LaunchProfileMenuAction(
          path = path,
          project = project,
          profileItem = profileItem,
          entryPoint = entryPoint,
          createNewSession = createNewSession,
          checkedLaunchProfileId = selection.checkedLaunchProfileId,
        ))
      }
    }
  }
  if (includeManageAction) {
    appendManageLaunchProfilesAction(actions)
  }
  return actions.toTypedArray()
}

fun buildAgentSessionLaunchProfileMenuRows(
  path: String,
  project: Project,
  selection: AgentSessionLaunchProfileSelection,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  includeManageAction: Boolean = true,
  event: AnActionEvent,
): List<AgentWorkbenchPopupRow> {
  val rows = mutableListOf<AgentWorkbenchPopupRow>()
  forEachLaunchProfileSection(selection.profiles) { title, sectionProfiles ->
    if (sectionProfiles.isNotEmpty()) {
      sectionProfiles.forEachIndexed { index, profileItem ->
        rows.add(createLaunchProfileMenuRow(
          path = path,
          project = project,
          profileItem = profileItem,
          entryPoint = entryPoint,
          createNewSession = createNewSession,
          checkedLaunchProfileId = selection.checkedLaunchProfileId,
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

fun quickStartActionText(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.bridge.quickStartActionTextKey, quickStartLabel(item))
}

fun quickStartLabel(item: AgentSessionProviderMenuItem): @Nls String {
  val labelKey = if (item.mode == AgentSessionLaunchMode.STANDARD) item.bridge.quickStartLabelKey else item.labelKey
  return AgentSessionsBundle.message(labelKey)
}

fun launchProfileActionText(item: AgentSessionLaunchProfileMenuItem): @Nls String {
  if (item.profile.kind == AgentPromptLaunchProfileKind.BUILT_IN) {
    return quickStartActionText(item.menuItem)
  }
  return AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewThreadProfileQuick.text", item.profile.name)
}

fun launchProfileActionDescription(
  profileItem: AgentSessionLaunchProfileMenuItem,
  projectLabel: @Nls String,
): @Nls String {
  if (!profileItem.menuItem.isEnabled) {
    return providerMenuItemDisabledReason(profileItem.menuItem)
  }
  return AgentSessionsBundle.message(
    "action.AgentWorkbenchSessions.MainToolbar.NewThread.profile.description",
    profileItem.profile.name,
    quickStartLabel(profileItem.menuItem),
    projectLabel,
  )
}

fun projectLabelForPath(path: String): @NlsSafe String {
  val trimmed = path.trimEnd('/')
  return trimmed.substringAfterLast('/').ifEmpty { trimmed }
}

private fun forEachLaunchProfileSection(
  profiles: List<AgentSessionLaunchProfileMenuItem>,
  handleSection: (@Nls String?, List<AgentSessionLaunchProfileMenuItem>) -> Unit,
) {
  val standardProfiles = profiles.filter { profileItem -> profileItem.profile.launchMode != AgentSessionLaunchMode.YOLO }
  val yoloProfiles = profiles.filter { profileItem -> profileItem.profile.launchMode == AgentSessionLaunchMode.YOLO }
  handleSection(null, standardProfiles)
  handleSection(AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"), yoloProfiles)
}

private fun createLaunchProfileMenuRow(
  path: String,
  project: Project,
  profileItem: AgentSessionLaunchProfileMenuItem,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  checkedLaunchProfileId: String?,
  separatorText: @Nls String?,
): AgentWorkbenchPopupRow {
  val isCheckedProfile = profileItem.profile.id == checkedLaunchProfileId
  val isEnabled = profileItem.menuItem.isEnabled
  return AgentWorkbenchPopupRow(
    text = profileItem.profile.name,
    separatorText = separatorText,
    primaryIcon = providerItemMonochromeIconWithMode(profileItem.menuItem),
    secondaryIcon = when {
      !isCheckedProfile -> null
      isEnabled -> LafIconLookup.getIcon("checkmark")
      else -> LafIconLookup.getDisabledIcon("checkmark")
    },
    tooltipText = launchProfileActionDescription(
      profileItem = profileItem,
      projectLabel = projectLabelForPath(path),
    ),
    selected = isCheckedProfile,
    selectable = isEnabled,
    onChosen = {
      if (isEnabled) {
        createNewSession(path, profileItem.profile, project, entryPoint)
      }
    },
  )
}

private class LaunchProfileMenuAction(
  private val path: String,
  private val project: Project,
  private val profileItem: AgentSessionLaunchProfileMenuItem,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val checkedLaunchProfileId: String?,
) : DumbAwareAction(profileItem.profile.name, null, providerItemMonochromeIconWithMode(profileItem.menuItem)) {
  init {
    setProviderItemLaunchProfileActiveMarker(templatePresentation, profileItem.menuItem, isCheckedProfile())
    templatePresentation.description = launchProfileActionDescription(
      profileItem = profileItem,
      projectLabel = projectLabelForPath(path),
    )
  }

  private fun isCheckedProfile(): Boolean {
    return profileItem.profile.id == checkedLaunchProfileId
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = profileItem.menuItem.isEnabled
    setProviderItemLaunchProfileActiveMarker(e.presentation, profileItem.menuItem, isCheckedProfile())
    e.presentation.description = launchProfileActionDescription(
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
