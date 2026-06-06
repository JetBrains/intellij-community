// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.providerItemIconWithMode
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Single split-button entry on `MainToolbarRight` that exposes "New Thread":
 * the icon shows the last-used provider+mode badge (or a generic `+` when no default exists yet).
 * Click on the icon zone quick-launches with the last-used provider+mode; click on the chevron
 * opens the provider × launch-mode picker. Uses the `actionSystem.SplitButtonAction` widget,
 * which paints the in-button separator only on hover/press — at rest the toolbar reads as a
 * single icon + chevron, no vertical line.
 */
internal class AgentSessionsMainToolbarNewThreadAction private constructor(
  resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  lastUsedProvider: () -> AgentSessionProvider?,
  lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  pickerGroup: PickerActionGroup,
  showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : AgentSessionsNewThreadSplitButtonAction(
  resolveContext = resolveContext,
  allBridges = allBridges,
  createNewSession = createNewSession,
  lastUsedProvider = lastUsedProvider,
  lastUsedLaunchMode = lastUsedLaunchMode,
  quickStartEntryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
  pickerGroup = pickerGroup,
  showPicker = showPicker,
  emptyTextKey = "action.AgentWorkbenchSessions.MainToolbar.NewThread.text",
  emptyDescriptionKey = "action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description",
  descriptionKey = "action.AgentWorkbenchSessions.MainToolbar.NewThread.description",
  targetChooseKey = "action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose",
  allowProviderFallback = true,
  includeTargetInDescription = true,
) {

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
    pickerGroup = PickerActionGroup(resolveContext, allBridges, createNewSession, AgentWorkbenchEntryPoint.TOOLBAR),
    showPicker = showPicker,
  )
}

internal class AgentSessionsEditorTabNewThreadAction private constructor(
  resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  lastUsedProvider: () -> AgentSessionProvider?,
  lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  pickerGroup: PickerActionGroup,
  showPicker: (ActionGroup, AnActionEvent) -> Unit,
) : AgentSessionsNewThreadSplitButtonAction(
  resolveContext = resolveContext,
  allBridges = allBridges,
  createNewSession = createNewSession,
  lastUsedProvider = lastUsedProvider,
  lastUsedLaunchMode = lastUsedLaunchMode,
  quickStartEntryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK,
  pickerGroup = pickerGroup,
  showPicker = showPicker,
  emptyTextKey = "action.AgentWorkbenchSessions.EditorTab.NewThread.text",
  emptyDescriptionKey = "action.AgentWorkbenchSessions.EditorTab.NewThread.empty.description",
  descriptionKey = "action.AgentWorkbenchSessions.EditorTab.NewThread.description",
  targetChooseKey = "action.AgentWorkbenchSessions.EditorTab.NewThread.target.choose",
  allowProviderFallback = false,
  includeTargetInDescription = false,
) {

  @JvmOverloads
  constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext? = { event ->
      resolveAgentSessionsEditorTabNewThreadContext(event)
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
    pickerGroup = PickerActionGroup(resolveContext, allBridges, createNewSession, AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP),
    showPicker = showPicker,
  )
}

class AgentSessionsDirectPathNewThreadAction private constructor(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
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
    createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit = ::createNewThreadViaService,
    lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
    lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedLaunchMode() },
    minimumButtonSize: (() -> Dimension)? = null,
    beforeAction: () -> Unit = {},
  ) : this(
    project = project,
    targetPath = targetPath,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    minimumButtonSize = minimumButtonSize,
    quickStartEntryPoint = quickStartEntryPoint,
    beforeAction = beforeAction,
    pickerGroup = DirectPathPickerActionGroup(
      project = project,
      targetPath = targetPath,
      allBridges = allBridges,
      createNewSession = createNewSession,
      lastUsedProvider = lastUsedProvider,
      lastUsedLaunchMode = lastUsedLaunchMode,
      popupEntryPoint = popupEntryPoint,
      beforeAction = beforeAction,
    ),
  )

  private val quickStartAction = DirectPathQuickStartAction(
    project = project,
    targetPath = targetPath,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
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
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode(), project)
    if (!actionModel.menuModel.hasEntries()) return null
    return if (actionModel.quickStartItem == null) null else quickStartAction
  }

  override fun update(e: AnActionEvent) {
    val path = targetPath()
    if (path == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode(), project)
    if (!actionModel.menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val quickStartItem = actionModel.quickStartItem
    e.presentation.icon = quickStartItem?.let(::providerItemIconWithMode) ?: AllIcons.General.Add
    e.presentation.text = quickStartItem
                            ?.let(::quickStartActionText)
                          ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text")
    e.presentation.description = quickStartItem
                                   ?.let(::quickStartActionDescription)
                                 ?: AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description")
    e.presentation.isEnabledAndVisible = true
  }
}

private class DirectPathPickerActionGroup(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  private val popupEntryPoint: AgentWorkbenchEntryPoint,
  private val beforeAction: () -> Unit,
) : ActionGroup(), DumbAware {
  init {
    templatePresentation.icon = AllIcons.General.Add
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val path = targetPath() ?: return emptyArray()
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode(), project)
    return buildNewThreadMenuActions(
      path = path,
      project = project,
      menuModel = actionModel.menuModel,
      entryPoint = popupEntryPoint,
    ) { targetPath, provider, mode, currentProject, entryPoint ->
      beforeAction()
      createNewSession(targetPath, provider, mode, currentProject, entryPoint)
    }
  }
}

private class DirectPathQuickStartAction(
  private val project: Project,
  private val targetPath: () -> String?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val beforeAction: () -> Unit,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val quickStartItem = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode(), project).quickStartItem
    if (targetPath() == null || quickStartItem == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true
    e.presentation.text = quickStartActionText(quickStartItem)
    e.presentation.description = quickStartActionDescription(quickStartItem)
    e.presentation.icon = providerItemIconWithMode(quickStartItem)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val path = targetPath() ?: return
    val quickStartItem = buildNewThreadActionModel(allBridges(), lastUsedProvider(), lastUsedLaunchMode(), project).quickStartItem ?: return
    beforeAction()
    launchQuickStartThread(path, project, quickStartItem, entryPoint, createNewSession)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal abstract class AgentSessionsNewThreadSplitButtonAction(
  private val resolveContext: (AnActionEvent) -> AgentSessionsEditorTabNewThreadContext?,
  private val allBridges: () -> List<AgentSessionProviderDescriptor>,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  quickStartEntryPoint: AgentWorkbenchEntryPoint,
  pickerGroup: PickerActionGroup,
  showPicker: (ActionGroup, AnActionEvent) -> Unit,
  private val emptyTextKey: String,
  private val emptyDescriptionKey: String,
  private val descriptionKey: String,
  private val targetChooseKey: String,
  private val allowProviderFallback: Boolean,
  private val includeTargetInDescription: Boolean,
) : SplitButtonAction(pickerGroup), DumbAware {

  private val quickStartAction = QuickStartAction(
    resolveContext = resolveContext,
    allBridges = allBridges,
    createNewSession = createNewSession,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    quickStartEntryPoint = quickStartEntryPoint,
    pickerGroup = pickerGroup,
    showPicker = showPicker,
    allowProviderFallback = allowProviderFallback,
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  public override fun getMainAction(e: AnActionEvent): AnAction? {
    val context = resolveContext(e) ?: return null
    resolveSplitButtonQuickStartItem(allBridges(), context.project) ?: return null
    return quickStartAction
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val bridges = allBridges()
    val menuModel = buildNewThreadMenuModel(bridges, context.project)
    if (!menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (e.updateSession !== UpdateSession.EMPTY) {
      super.update(e)
    }
    val quickStartItem = resolveSplitButtonQuickStartItem(menuModel)
    e.presentation.icon = quickStartItem
                            ?.let(::providerItemIconWithMode)
                          ?: AllIcons.General.Add
    e.presentation.text = quickStartItem
                            ?.let(::quickStartActionText)
                          ?: AgentSessionsBundle.message(emptyTextKey)
    val targetForDescription = if (includeTargetInDescription) context.targetForUpdate else null
    e.presentation.description = describeTooltip(quickStartItem, targetForDescription)
    e.presentation.isEnabledAndVisible = true
  }

  private fun resolveSplitButtonQuickStartItem(
    bridges: List<AgentSessionProviderDescriptor>,
    project: Project,
  ): AgentSessionProviderMenuItem? {
    return resolveSplitButtonQuickStartItem(buildNewThreadMenuModel(bridges, project))
  }

  private fun resolveSplitButtonQuickStartItem(menuModel: AgentSessionProviderMenuModel): AgentSessionProviderMenuItem? {
    return resolveSplitButtonQuickStartItem(menuModel, lastUsedProvider(), lastUsedLaunchMode(), allowProviderFallback)
  }

  private fun describeTooltip(
    quickStartItem: AgentSessionProviderMenuItem?,
    target: AgentSessionsEditorTabNewThreadTarget?,
  ): @Nls String {
    if (quickStartItem == null) {
      return AgentSessionsBundle.message(emptyDescriptionKey)
    }
    if (!includeTargetInDescription) {
      return quickStartActionDescription(quickStartItem)
    }
    val providerLabel = quickStartLabel(quickStartItem)
    val projectLabel = when (target) {
      is AgentSessionsEditorTabNewThreadTarget.Direct -> projectLabelForPath(target.path)
      is AgentSessionsEditorTabNewThreadTarget.Candidates, null ->
        AgentSessionsBundle.message(targetChooseKey)
    }
    val targetDescriptionKey = quickStartItem.bridge.quickStartActionTargetDescriptionKey
    if (targetDescriptionKey != null) {
      return AgentSessionsBundle.message(targetDescriptionKey, providerLabel, projectLabel)
    }
    return AgentSessionsBundle.message(
      descriptionKey,
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
  private val entryPoint: AgentWorkbenchEntryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
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
        entryPoint = entryPoint,
        createNewSession = createNewSession,
      )
      is AgentSessionsEditorTabNewThreadTarget.Candidates -> target.candidates.map { candidate ->
        buildProjectCandidatePopupGroup(
          candidate = candidate,
          project = context.project,
          menuModel = menuModel,
          entryPoint = entryPoint,
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
  private val quickStartEntryPoint: AgentWorkbenchEntryPoint,
  private val pickerGroup: ActionGroup,
  private val showPicker: (ActionGroup, AnActionEvent) -> Unit,
  private val allowProviderFallback: Boolean,
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
    val menuModel = buildNewThreadMenuModel(allBridges(), context.project)
    val quickStartItem = resolveSplitButtonQuickStartItem(
      menuModel = menuModel,
      lastUsedProvider = provider,
      lastUsedLaunchMode = lastUsedLaunchMode(),
      allowProviderFallback = allowProviderFallback,
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
      val disabledReasonKey = quickStartItem.disabledReasonKey
      if (disabledReasonKey != null) AgentSessionsBundle.message(disabledReasonKey) else quickStartActionDescription(quickStartItem)
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
          entryPoint = quickStartEntryPoint,
          createNewSession = createNewSession,
        )
      }
      is AgentSessionsEditorTabNewThreadTarget.Candidates, null -> showPicker(pickerGroup, e)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveReadyQuickStartItem(bridges: List<AgentSessionProviderDescriptor>, project: Project): AgentSessionProviderMenuItem? {
    return resolveSplitButtonQuickStartItem(
      menuModel = buildNewThreadMenuModel(bridges, project),
      lastUsedProvider = lastUsedProvider(),
      lastUsedLaunchMode = lastUsedLaunchMode(),
      allowProviderFallback = allowProviderFallback,
    )?.takeIf { item -> item.isEnabled }
  }

  private fun hide(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }
}

private fun resolveSplitButtonQuickStartItem(
  menuModel: AgentSessionProviderMenuModel,
  lastUsedProvider: AgentSessionProvider?,
  lastUsedLaunchMode: AgentSessionLaunchMode?,
  allowProviderFallback: Boolean,
): AgentSessionProviderMenuItem? {
  if (lastUsedLaunchMode == AgentSessionLaunchMode.YOLO) {
    val preferredYoloItem = lastUsedProvider?.let { provider ->
      menuModel.yoloItems.firstOrNull { item -> item.bridge.provider == provider }
    }
    if (preferredYoloItem != null) return preferredYoloItem
  }
  val preferredStandardItem = lastUsedProvider?.let { provider ->
    menuModel.standardItems.firstOrNull { item -> item.bridge.provider == provider }
  }
  return preferredStandardItem ?: menuModel.standardItems.firstOrNull().takeIf { allowProviderFallback }
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

private fun projectLabelForPath(path: String): String {
  val trimmed = path.trimEnd('/')
  return trimmed.substringAfterLast('/').ifEmpty { trimmed }
}
