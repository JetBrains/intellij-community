// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
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
  innerGroup: PickerActionGroup,
) : SplitButtonAction(innerGroup), DumbAware {

  @JvmOverloads
  constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? = { event -> resolveAgentSessionsMainToolbarNewThreadContext(event) },
    allBridges: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
    createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
    lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
  ) : this(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    innerGroup = PickerActionGroup(resolveContext, allBridges, createNewSession),
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    val context = resolveContext(e) ?: return null
    val target = context.target as? AgentSessionsEditorTabNewThreadTarget.Direct ?: return null
    val quickStartItem = resolveQuickStartItem(allBridges()) ?: return null
    return QuickStartAction(
      project = context.project,
      path = target.path,
      quickStartItem = quickStartItem,
      createNewSession = createNewSession,
    )
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val bridges = allBridges()
    val menuModel = buildNewThreadMenuModel(bridges)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val quickStartItem = resolveQuickStartItem(bridges)
    e.presentation.icon = quickStartItem
      ?.let(::providerItemIconWithMode)
      ?: AllIcons.General.Add
    e.presentation.description = describeTooltip(quickStartItem, context.target)
    e.presentation.isEnabledAndVisible = true
  }

  private fun resolveQuickStartItem(bridges: List<AgentSessionProviderDescriptor>): AgentSessionProviderMenuItem? {
    val provider = lastUsedProvider() ?: return null
    return buildNewThreadActionModel(bridges, provider, lastUsedLaunchMode()).quickStartItem
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
    val menuModel = buildNewThreadMenuModel(allBridges())
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
  private val project: Project,
  private val path: String,
  private val quickStartItem: AgentSessionProviderMenuItem,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) : DumbAwareAction(
  AgentSessionsBundle.message(
    "action.AgentWorkbenchSessions.MainToolbar.NewThread.last.used",
    AgentSessionsBundle.message(quickStartItem.labelKey),
  ),
  null,
  providerItemIconWithMode(quickStartItem),
) {
  override fun actionPerformed(e: AnActionEvent) {
    launchQuickStartThread(
      path = path,
      project = project,
      quickStartItem = quickStartItem,
      entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
      createNewSession = createNewSession,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
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
