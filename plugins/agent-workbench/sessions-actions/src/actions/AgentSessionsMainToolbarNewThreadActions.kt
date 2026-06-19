// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.AgentSessionLaunchProfileMenuItem
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.appendManageLaunchProfilesAction
import com.intellij.agent.workbench.sessions.appendManageLaunchProfilesRow
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuActions
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuModel
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuRows
import com.intellij.agent.workbench.sessions.launchProfileActionDescription
import com.intellij.agent.workbench.sessions.launchProfileActionText
import com.intellij.agent.workbench.sessions.launchQuickStartProfile
import com.intellij.agent.workbench.sessions.projectLabelForPath
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.resolveExplicitAgentSessionLaunchProfileItem
import com.intellij.agent.workbench.sessions.resolveAgentSessionLaunchProfileItem
import com.intellij.agent.workbench.sessions.resolveAgentSessionLaunchProfileItems
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.ui.AgentWorkbenchPopupRow
import com.intellij.agent.workbench.ui.createAgentWorkbenchListPopup
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.SplitButtonAction
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ClientProperty
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Single split-button entry on `MainToolbarRight` that exposes "New Thread":
 * the icon shows the default launch profile provider+mode badge (or a generic `+` when no default exists yet).
 * Click on the icon zone quick-launches with the default launch profile; click on the chevron
 * opens the launch-profile picker. Uses the `actionSystem.SplitButtonAction` widget,
 * which paints the in-button separator only on hover/press — at rest the toolbar reads as a
 * single icon + chevron, no vertical line.
 */
internal class AgentSessionsMainToolbarNewThreadAction private constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  pickerGroup: ProfilePickerActionGroup,
  showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : SplitButtonAction(pickerGroup), DumbAware {

  private val quickStartAction = ProfileQuickStartAction(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
    pickerGroup = pickerGroup,
    showPicker = showPicker,
    fallbackToFirstProfile = false,
    showPickerWhenProfileUnavailable = true,
  )

  @JvmOverloads
  constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? = { event ->
      resolveAgentSessionsMainToolbarNewThreadContext(event)
    },
    allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
    createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles() },
    defaultLaunchProfileId: () -> String? = { service<AgentSessionUiPreferencesStateService>().getDefaultLaunchProfileId() },
    showPicker: (ActionGroup, AnActionEvent) -> Unit = ::showToolbarProfilePicker,
  ) : this(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    pickerGroup = ProfilePickerActionGroup(
      resolveContext = resolveContext,
      allBridges = allBridges,
      createNewSession = createNewSession,
      userLaunchProfiles = userLaunchProfiles,
      defaultLaunchProfileId = defaultLaunchProfileId,
      entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
      markFallbackProfile = false,
    ),
    showPicker = showPicker,
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    val context = resolveContext(e) ?: return null
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), context.project)
    if (!menuModel.hasEntries()) return null
    if (resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles()).isEmpty()) return null
    return quickStartAction
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val bridges = allBridges()
    val menuModel = buildAgentSessionLaunchProfileMenuModel(bridges, context.project)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val defaultProfileId = defaultLaunchProfileId()
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    if (profiles.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val quickStart = resolveExplicitAgentSessionLaunchProfileItem(profiles, defaultProfileId)
    e.presentation.icon = quickStart?.menuItem?.let(::providerItemMonochromeIconWithMode) ?: AllIcons.General.Add
    e.presentation.text = quickStart?.let(::launchProfileActionText)
                          ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text")
    e.presentation.description = if (quickStart == null) describeMissingDefaultProfileTooltip() else describeProfileTooltip(quickStart, context.targetForUpdate)
    e.presentation.isEnabledAndVisible = true
  }

  @TestOnly
  fun createProfilePickerRowsForTest(e: AnActionEvent): List<AgentWorkbenchPopupRow> {
    return (actionGroup as ProfilePickerActionGroup).createRows(e)
  }
}

internal class AgentSessionsEditorTabNewThreadAction private constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  pickerGroup: ProfilePickerActionGroup,
  showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : SplitButtonAction(pickerGroup), DumbAware {

  private val quickStartAction = ProfileQuickStartAction(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK,
    pickerGroup = pickerGroup,
    showPicker = showPicker,
  )

  @JvmOverloads
  constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? = { event ->
      resolveAgentSessionsEditorTabNewThreadContext(event)
    },
    allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
    createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles() },
    defaultLaunchProfileId: () -> String? = { service<AgentSessionUiPreferencesStateService>().getDefaultLaunchProfileId() },
    showPicker: (ActionGroup, AnActionEvent) -> Unit = ::showToolbarProfilePicker,
  ) : this(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    pickerGroup = ProfilePickerActionGroup(
      resolveContext = resolveContext,
      allBridges = allBridges,
      createNewSession = createNewSession,
      userLaunchProfiles = userLaunchProfiles,
      defaultLaunchProfileId = defaultLaunchProfileId,
      entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
    ),
    showPicker = showPicker,
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    val context = resolveContext(e) ?: return null
    resolveAgentSessionLaunchProfileItem(allBridges(), context.project, userLaunchProfiles(), defaultLaunchProfileId()) ?: return null
    return quickStartAction
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val bridges = allBridges()
    val menuModel = buildAgentSessionLaunchProfileMenuModel(bridges, context.project)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val defaultProfileId = defaultLaunchProfileId()
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    if (profiles.isEmpty()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val quickStart = resolveAgentSessionLaunchProfileItem(profiles, defaultProfileId)
    e.presentation.icon = quickStart?.menuItem?.let(::providerItemMonochromeIconWithMode) ?: AllIcons.General.Add
    e.presentation.text = quickStart?.let(::launchProfileActionText)
                          ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.EditorTab.NewThread.text")
    e.presentation.description = describeProfileTooltip(quickStart, context.targetForUpdate)
    e.presentation.isEnabledAndVisible = true
  }
}

private class ProfilePickerActionGroup(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  private val entryPoint: AgentWorkbenchEntryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
  private val markFallbackProfile: Boolean = true,
) : ActionGroup(), DumbAware {
  init {
    templatePresentation.icon = AllIcons.General.Add
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val event = e ?: return emptyArray()
    val context = resolveContext(event) ?: return emptyArray()
    val target = context.target ?: return emptyArray()
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), context.project)
    if (!menuModel.hasEntries()) return emptyArray()
    val defaultProfileId = defaultLaunchProfileId()
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    if (profiles.isEmpty()) return emptyArray()
    val checkedProfileId = checkedLaunchProfileId(profiles, defaultProfileId)
    return when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> buildAgentSessionLaunchProfileMenuActions(
        path = target.path,
        project = context.project,
        profiles = profiles,
        entryPoint = entryPoint,
        createNewSession = createNewSession,
        checkedLaunchProfileId = checkedProfileId,
      )
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> target.candidates.mapTo(mutableListOf<AnAction>()) { candidate ->
        DefaultActionGroup(candidate.displayName, true).apply {
          buildAgentSessionLaunchProfileMenuActions(
            path = candidate.path,
            project = context.project,
            profiles = profiles,
            entryPoint = entryPoint,
            createNewSession = createNewSession,
            checkedLaunchProfileId = checkedProfileId,
            includeManageAction = false,
          ).forEach(::add)
        }
      }.let { actions -> appendManageLaunchProfilesAction(actions) }.toTypedArray<AnAction>()
    }
  }

  fun createRows(e: AnActionEvent): List<AgentWorkbenchPopupRow> {
    val context = resolveContext(e) ?: return emptyList()
    val target = context.target ?: return emptyList()
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), context.project)
    if (!menuModel.hasEntries()) return emptyList()
    val defaultProfileId = defaultLaunchProfileId()
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    if (profiles.isEmpty()) return emptyList()
    val checkedProfileId = checkedLaunchProfileId(profiles, defaultProfileId)
    return when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> buildAgentSessionLaunchProfileMenuRows(
        path = target.path,
        project = context.project,
        profiles = profiles,
        entryPoint = entryPoint,
        createNewSession = createNewSession,
        checkedLaunchProfileId = checkedProfileId,
        event = e,
      )
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> target.candidates.map { candidate ->
        AgentWorkbenchPopupRow(
          text = candidate.displayName,
          tooltipText = candidate.path.takeIf { path -> path != candidate.displayName },
          secondaryIcon = AllIcons.General.ArrowRight,
          subRows = buildAgentSessionLaunchProfileMenuRows(
            path = candidate.path,
            project = context.project,
            profiles = profiles,
            entryPoint = entryPoint,
            createNewSession = createNewSession,
            checkedLaunchProfileId = checkedProfileId,
            includeManageAction = false,
            event = e,
          ),
        )
      }.let { rows -> appendManageLaunchProfilesRow(rows.toMutableList(), e) }
    }
  }

  private fun checkedLaunchProfileId(
    profiles: List<AgentSessionLaunchProfileMenuItem>,
    defaultProfileId: String?,
  ): String? {
    val checkedProfile = if (markFallbackProfile) {
      resolveAgentSessionLaunchProfileItem(profiles, defaultProfileId)
    }
    else {
      resolveExplicitAgentSessionLaunchProfileItem(profiles, defaultProfileId)
    }
    return checkedProfile?.profile?.id
  }
}

internal class ProfileQuickStartAction(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val pickerGroup: ActionGroup,
  private val showPicker: (ActionGroup, AnActionEvent) -> Unit,
  private val fallbackToFirstProfile: Boolean = true,
  private val showPickerWhenProfileUnavailable: Boolean = false,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val quickStart = context?.let(::resolveQuickStartItem)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (quickStart == null) {
      e.presentation.isEnabledAndVisible = showPickerWhenProfileUnavailable
      e.presentation.text = AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text")
      e.presentation.description = describeMissingDefaultProfileTooltip()
      e.presentation.icon = AllIcons.General.Add
      return
    }
    e.presentation.isEnabledAndVisible = quickStart.menuItem.isEnabled
    e.presentation.text = launchProfileActionText(quickStart)
    e.presentation.description = describeProfileTooltip(quickStart, context.targetForUpdate)
    e.presentation.icon = providerItemMonochromeIconWithMode(quickStart.menuItem)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (val target = context.target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> {
        val quickStart = resolveQuickStartItem(context)
        if (quickStart == null || !quickStart.menuItem.isEnabled) {
          showPicker(pickerGroup, e)
          return
        }
        createNewSession(target.path, quickStart.profile, context.project, entryPoint)
      }
      is AgentSessionsEditorTabNewThreadTarget.Candidates, null -> showPicker(pickerGroup, e)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveQuickStartItem(context: AgentSessionsEditorTabNewThreadContext): AgentSessionLaunchProfileMenuItem? {
    val defaultProfileId = defaultLaunchProfileId()
    if (fallbackToFirstProfile) {
      return resolveAgentSessionLaunchProfileItem(allBridges(), context.project, userLaunchProfiles(), defaultProfileId)
    }
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), context.project)
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    return resolveExplicitAgentSessionLaunchProfileItem(profiles, defaultProfileId)
  }
}

class AgentSessionsDirectPathNewThreadAction private constructor(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  private val minimumButtonSize: (() -> Dimension)?,
  quickStartEntryPoint: AgentWorkbenchEntryPoint,
  beforeAction: () -> Unit,
  pickerGroup: DirectPathPickerActionGroup,
) : SplitButtonAction(pickerGroup), DumbAware {

  @JvmOverloads
  constructor(
    project: Project,
    targetPath: () -> String?,
    quickStartEntryPoint: AgentWorkbenchEntryPoint,
    popupEntryPoint: AgentWorkbenchEntryPoint,
    allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
    createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionUiPreferencesStateService>().getUserLaunchProfiles() },
    defaultLaunchProfileId: () -> String? = { service<AgentSessionUiPreferencesStateService>().getDefaultLaunchProfileId() },
    minimumButtonSize: (() -> Dimension)? = null,
    beforeAction: () -> Unit = {},
  ) : this(
    project = project,
    targetPath = targetPath,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    minimumButtonSize = minimumButtonSize,
    quickStartEntryPoint = quickStartEntryPoint,
    beforeAction = beforeAction,
    pickerGroup = DirectPathPickerActionGroup(
      project = project,
      targetPath = targetPath,
      allBridges = allBridges,
      createNewSession = createNewSession,
      userLaunchProfiles = userLaunchProfiles,
      defaultLaunchProfileId = defaultLaunchProfileId,
      popupEntryPoint = popupEntryPoint,
      beforeAction = beforeAction,
    ),
  )

  private val quickStartAction = DirectPathQuickStartAction(
    project = project,
    targetPath = targetPath,
    allBridges = allBridges,
    createNewSession = createNewSession,
    userLaunchProfiles = userLaunchProfiles,
    defaultLaunchProfileId = defaultLaunchProfileId,
    entryPoint = quickStartEntryPoint,
    beforeAction = beforeAction,
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = super.createCustomComponent(presentation, place)
    ClientProperty.put(component, ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true)
    val minimumButtonSize = minimumButtonSize
    if (minimumButtonSize != null && component is ActionButton) {
      component.setMinimumButtonSize { minimumButtonSize() }
    }
    return component
  }

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    if (targetPath() == null) return null
    val quickStartItem = resolveDirectPathQuickStartItem()
    return if (quickStartItem?.menuItem?.isEnabled == true) quickStartAction else null
  }

  override fun update(e: AnActionEvent) {
    val path = targetPath()
    if (path == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), project)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val quickStartItem = resolveDirectPathQuickStartItem(menuModel)
    e.presentation.icon = quickStartItem?.menuItem?.let(::providerItemMonochromeIconWithMode) ?: AllIcons.General.Add
    e.presentation.text = quickStartItem
      ?.let(::launchProfileActionText)
                          ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text")
    e.presentation.description = quickStartItem
                                   ?.let { profileItem ->
                                      launchProfileActionDescription(profileItem, projectLabelForPath(path))
                                   }
                                 ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description")
    e.presentation.isEnabledAndVisible = true
  }

  private fun resolveDirectPathQuickStartItem(
    menuModel: AgentSessionProviderMenuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), project),
  ): AgentSessionLaunchProfileMenuItem? {
    return resolveAgentSessionLaunchProfileItem(menuModel, userLaunchProfiles(), defaultLaunchProfileId())
  }
}

private class DirectPathPickerActionGroup(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  private val popupEntryPoint: AgentWorkbenchEntryPoint,
  private val beforeAction: () -> Unit,
) : ActionGroup(), DumbAware {
  init {
    templatePresentation.icon = AllIcons.General.Add
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val path = targetPath() ?: return emptyArray()
    val menuModel = buildAgentSessionLaunchProfileMenuModel(allBridges(), project)
    val defaultProfileId = defaultLaunchProfileId()
    val profiles = resolveAgentSessionLaunchProfileItems(menuModel, userLaunchProfiles())
    if (profiles.isEmpty()) return emptyArray()
    val checkedProfileId = resolveAgentSessionLaunchProfileItem(profiles, defaultProfileId)?.profile?.id
    return buildAgentSessionLaunchProfileMenuActions(
      path = path,
      project = project,
      profiles = profiles,
      entryPoint = popupEntryPoint,
      createNewSession = { targetPath, profile, currentProject, entryPoint ->
        beforeAction()
        createNewSession(targetPath, profile, currentProject, entryPoint)
      },
      checkedLaunchProfileId = checkedProfileId,
    )
  }
}

private class DirectPathQuickStartAction(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentPromptLaunchProfile, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile>,
  private val defaultLaunchProfileId: () -> String?,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val beforeAction: () -> Unit,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val path = targetPath()
    val quickStartItem = resolveReadyQuickStartItem()
    if (path == null || quickStartItem == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true
    e.presentation.text = launchProfileActionText(quickStartItem)
    e.presentation.description = launchProfileActionDescription(quickStartItem, projectLabelForPath(path))
    e.presentation.icon = providerItemMonochromeIconWithMode(quickStartItem.menuItem)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val path = targetPath() ?: return
    val quickStartItem = resolveReadyQuickStartItem() ?: return
    beforeAction()
    launchQuickStartProfile(path, project, quickStartItem, entryPoint, createNewSession)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveReadyQuickStartItem(): AgentSessionLaunchProfileMenuItem? {
    return resolveAgentSessionLaunchProfileItem(allBridges(), project, userLaunchProfiles(), defaultLaunchProfileId())
      ?.takeIf { profileItem -> profileItem.menuItem.isEnabled }
  }
}

private fun describeMissingDefaultProfileTooltip(): @Nls String {
  return AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.default.missing.description")
}

private fun describeProfileTooltip(
  quickStart: AgentSessionLaunchProfileMenuItem?,
  target: AgentSessionsEditorTabNewThreadTarget?,
): @Nls String {
  if (quickStart == null) {
    return AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description")
  }
  val projectLabel = when (target) {
    is AgentSessionsEditorTabNewThreadTarget.Direct -> projectLabelForPath(target.path)
    is AgentSessionsEditorTabNewThreadTarget.Candidates, null ->
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose")
  }
  return launchProfileActionDescription(quickStart, projectLabel)
}

private fun showToolbarPicker(group: ActionGroup, e: AnActionEvent) {
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

private fun showToolbarProfilePicker(group: ActionGroup, e: AnActionEvent) {
  if (group !is ProfilePickerActionGroup) {
    showToolbarPicker(group, e)
    return
  }
  val rows = group.createRows(e)
  if (rows.isEmpty()) return
  val popup = createAgentWorkbenchListPopup(null, rows)
  val anchor = resolveQuickStartProjectPopupAnchor(e)
  if (anchor != null) {
    popup.showUnderneathOf(anchor)
  }
  else {
    popup.showInBestPositionFor(e.dataContext)
  }
}
