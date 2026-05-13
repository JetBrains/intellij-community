// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.SplitButtonAction
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Single split-button entry on `MainToolbarRight` that exposes "New Thread":
 * the icon shows the last-used provider+mode badge (or a generic `+` when no default exists yet).
 * Click on the icon zone quick-launches with the last-used provider+mode; click on the chevron
 * opens the provider × launch-mode picker. Uses the `actionSystem.SplitButtonAction` widget,
 * which paints the in-button separator only on hover/press — at rest the toolbar reads as a
 * single icon + chevron, no vertical line.
 */
internal class AgentSessionsMainToolbarNewThreadAction private constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  private val pickerGroup: PickerActionGroup,
  private val showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : SplitButtonAction(pickerGroup), DumbAware {

  private val quickStartAction = QuickStartAction(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    pickerGroup = pickerGroup,
    showPicker = showPicker,
  )

  @JvmOverloads
  constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? = { event ->
      resolveAgentSessionsMainToolbarNewThreadContext(event)
    },
    allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
    createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
    lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
    showPicker: (ActionGroup, AnActionEvent) -> Unit = ::showToolbarPicker,
  ) : this(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    pickerGroup = PickerActionGroup(resolveContext, allBridges, createNewSession),
    showPicker = showPicker,
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    resolveContext(e) ?: return null
    resolveToolbarQuickStartItem(allBridges()) ?: return null
    return quickStartAction
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val bridges = allBridges()
    val menuModel = buildToolbarNewThreadMenuModel(bridges)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val quickStartItem = resolveToolbarQuickStartItem(menuModel)
    e.presentation.icon = quickStartItem
                            ?.let(::providerItemIconWithMode)
                          ?: AllIcons.General.Add
    e.presentation.text = quickStartItem
                            ?.let(::quickStartActionText)
                          ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text")
    e.presentation.description = describeTooltip(quickStartItem, context.targetForUpdate)
    e.presentation.isEnabledAndVisible = true
  }

  private fun resolveToolbarQuickStartItem(bridges: List<AgentSessionProviderDescriptor>): AgentSessionProviderMenuItem? {
    return resolveToolbarQuickStartItem(buildToolbarNewThreadMenuModel(bridges))
  }

  private fun resolveToolbarQuickStartItem(menuModel: AgentSessionProviderMenuModel): AgentSessionProviderMenuItem? {
    val provider = lastUsedProvider() ?: return null
    return resolveToolbarQuickStartItem(menuModel, provider, lastUsedLaunchMode())
  }

  private fun describeTooltip(
    quickStartItem: AgentSessionProviderMenuItem?,
    target: AgentSessionsEditorTabNewThreadTarget?,
  ): @Nls String {
    if (quickStartItem == null) {
      return AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description")
    }
    val providerLabel = AgentSessionsBundle.message(quickStartItem.labelKey)
    val projectLabel = when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> projectLabelForPath(target.path)
      is AgentSessionsEditorTabNewThreadTarget.Candidates, null ->
        AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose")
    }
    return AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.MainToolbar.NewThread.description",
      providerLabel,
      projectLabel,
    )
  }
}

/**
 * Inner action group whose children become the chevron's popup menu.
 * Not registered as an action; handed to [SplitButtonAction] via its constructor.
 */
internal class PickerActionGroup(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) : ActionGroup(), DumbAware {
  init {
    templatePresentation.icon = AllIcons.General.Add
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val event = e ?: return emptyArray()
    val context = resolveContext(event) ?: return emptyArray()
    val target = context.target ?: return emptyArray()
    val menuModel = buildNewThreadMenuModel(allBridges(), context.project)
    if (!menuModel.hasEntries()) return emptyArray()

    return when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> buildNewThreadMenuActions(
        path = target.path,
        project = context.project,
        menuModel = menuModel,
        entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
        createNewSession = createNewSession,
      )
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> target.candidates.map { candidate ->
        buildProjectCandidatePopupGroup(
          candidate = candidate,
          project = context.project,
          menuModel = menuModel,
          entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
          createNewSession = createNewSession,
        )
      }.toTypedArray<AnAction>()
    }
  }
}

internal class QuickStartAction(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  private val pickerGroup: ActionGroup,
  private val showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val provider = lastUsedProvider()
    if (provider == null) {
      hide(e)
      return
    }
    val menuModel = buildNewThreadActionModel(allBridges(), provider, lastUsedLaunchMode(), context.project).menuModel
    val quickStartItem = resolveToolbarQuickStartItem(
      menuModel = menuModel,
      lastUsedProvider = provider,
      lastUsedLaunchMode = lastUsedLaunchMode(),
    )
    if (quickStartItem == null) {
      hide(e)
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = quickStartItem.isEnabled
    e.presentation.text = quickStartActionText(quickStartItem)
    e.presentation.description = if (quickStartItem.isEnabled) {
      quickStartActionDescription(quickStartItem)
    }
    else {
      quickStartItem.disabledReasonKey?.let(AgentSessionsBundle::message) ?: quickStartActionDescription(quickStartItem)
    }
    e.presentation.icon = providerItemIconWithMode(quickStartItem)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    when (val target = context.target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> {
        val quickStartItem = resolveReadyQuickStartItem(allBridges(), context.project)
        if (quickStartItem == null) {
          showPicker(pickerGroup, e)
          return
        }
        launchQuickStartThread(
          path = target.path,
          project = context.project,
          quickStartItem = quickStartItem,
          entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
          createNewSession = createNewSession,
        )
      }
      is AgentSessionsEditorTabNewThreadTarget.Candidates, null -> showPicker(pickerGroup, e)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveReadyQuickStartItem(bridges: List<AgentSessionProviderDescriptor>, project: Project): AgentSessionProviderMenuItem? {
    val provider = lastUsedProvider() ?: return null
    return buildNewThreadActionModel(bridges, provider, lastUsedLaunchMode(), project).quickStartItem
  }

  private fun hide(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }
}

private fun buildToolbarNewThreadMenuModel(bridges: List<AgentSessionProviderDescriptor>): AgentSessionProviderMenuModel {
  val standardItems = ArrayList<AgentSessionProviderMenuItem>(bridges.size)
  val yoloItems = ArrayList<AgentSessionProviderMenuItem>()
  for (bridge in bridges) {
    if (AgentSessionLaunchMode.STANDARD in bridge.supportedLaunchModes) {
      standardItems += AgentSessionProviderMenuItem(
        bridge = bridge,
        mode = AgentSessionLaunchMode.STANDARD,
        labelKey = bridge.newSessionLabelKey,
        isEnabled = true,
      )
    }
    val yoloLabelKey = bridge.yoloSessionLabelKey
    if (yoloLabelKey != null && AgentSessionLaunchMode.YOLO in bridge.supportedLaunchModes) {
      yoloItems += AgentSessionProviderMenuItem(
        bridge = bridge,
        mode = AgentSessionLaunchMode.YOLO,
        labelKey = yoloLabelKey,
        isEnabled = true,
      )
    }
  }
  return AgentSessionProviderMenuModel(standardItems = standardItems, yoloItems = yoloItems)
}

private fun resolveToolbarQuickStartItem(
  menuModel: AgentSessionProviderMenuModel,
  lastUsedProvider: AgentSessionProvider,
  lastUsedLaunchMode: AgentSessionLaunchMode?,
): AgentSessionProviderMenuItem? {
  if (lastUsedLaunchMode == AgentSessionLaunchMode.YOLO) {
    val preferredYoloItem = menuModel.yoloItems.firstOrNull { item -> item.bridge.provider == lastUsedProvider }
    if (preferredYoloItem != null) return preferredYoloItem
  }
  val preferredStandardItem = menuModel.standardItems.firstOrNull { item -> item.bridge.provider == lastUsedProvider }
  return preferredStandardItem ?: menuModel.standardItems.firstOrNull()
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

private fun providerItemIconWithMode(item: AgentSessionProviderMenuItem): Icon {
  val icon = item.bridge.icon
  if (item.mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}

private fun projectLabelForPath(path: String): String {
  val trimmed = path.trimEnd('/')
  return trimmed.substringAfterLast('/').ifEmpty { trimmed }
}
