// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.AgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.AgentSessionLaunchProfileMenuItem
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuActions
import com.intellij.agent.workbench.sessions.buildAgentSessionLaunchProfileMenuModel
import com.intellij.platform.ai.agent.sessions.core.providers.builtInLaunchProfileId
import com.intellij.platform.ai.agent.sessions.core.providers.generationSettingsForPlanMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.providerMenuItemDisabledReason
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.resolveAgentSessionLaunchProfileSelection
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

private data class ResolveWithAgentContext(
  val project: Project,
  val selectionHintFiles: List<VirtualFile>,
  val closeDialog: (() -> Unit)?,
)

private sealed interface QuickLaunchResult {
  data object NoContext : QuickLaunchResult
  data object NoProvidersShown : QuickLaunchResult
  data object Launched : QuickLaunchResult
}

private const val ONE_SHOT_DIALOG_ACTION_PLACE: String = "Merge.OneShotDialog"
private const val ITERATIVE_DIALOG_ACTION_PLACE: String = "Merge.Dialog.Iterative"

internal class AgentResolveConflictsAction @JvmOverloads constructor(
  private val allProviders: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val userLaunchProfiles: () -> List<AgentPromptLaunchProfile> = { service<AgentSessionLaunchProfileStateService>().getUserLaunchProfiles() },
  private val activeVcsMergeLaunchProfileId: () -> String? = {
    service<AgentSessionLaunchProfileStateService>().getActiveVcsMergeLaunchProfileId()
  },
  private val setActiveVcsMergeLaunchProfileId: (String?) -> Unit = { profileId ->
    service<AgentSessionLaunchProfileStateService>().setActiveVcsMergeLaunchProfileId(profileId)
  },
  private val startSession: (Project, AgentVcsMergeLaunchRequest) -> Unit = ::startAgentMergeSession,
) : DumbAwareAction(), CustomComponentAction {
  // Captured during update(); reused by ResolveWithAgentOptionButton.buildOptionActions when its
  // freshly-resolved data context lacks the merge context (e.g. when the button is not yet attached
  // to a parent component hierarchy).
  private val latestUpdateProject = AtomicReference<Project?>()

  init {
    templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val context = resolveContext(e.dataContext)
    presentation.isVisible = context != null
    if (context == null) {
      return
    }
    latestUpdateProject.set(context.project)

    val menuModel = buildProviderMenuModel(context.project)
    val selection = resolveVcsLaunchProfileSelection(menuModel, context.project)
    val enabledProfileItems = enabledProfileItems(selection.profiles)
    val quickStartItem = selection.quickStartItem

    presentation.isEnabled = enabledProfileItems.isNotEmpty()
    presentation.description = when {
      presentation.isEnabled -> templatePresentation.description
      menuItems(menuModel).isEmpty() -> AgentVcsMergeBundle.message("merge.agent.resolve.no.providers")
      else -> menuItems(menuModel).firstNotNullOfOrNull(::disabledDescription)
              ?: AgentVcsMergeBundle.message("merge.agent.resolve.no.providers")
    }
    presentation.icon = quickStartItem?.menuItem?.let { providerItemMonochromeIconWithMode(it) }
                        ?: enabledProfileItems.firstOrNull()?.menuItem?.let { providerItemMonochromeIconWithMode(it) }
                        ?: AllIcons.Actions.InlayGear
  }

  override fun actionPerformed(e: AnActionEvent) {
    resolveAndQuickLaunch(e.dataContext)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    if (!isMergeDialogActionPlace(place)) {
      return ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }
    return ResolveWithAgentOptionButton(place).apply {
      updateFromPresentation(presentation)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    when (component) {
      is ResolveWithAgentOptionButton -> component.updateFromPresentation(presentation)
    }
  }

  private fun launchResolution(context: ResolveWithAgentContext, item: AgentSessionLaunchProfileMenuItem) {
    val provider = AgentSessionProvider.fromOrNull(item.profile.providerId) ?: return
    launchAgentMergeResolution(
      project = context.project,
      request = AgentVcsMergeLaunchRequest(
        selectionHintFiles = context.selectionHintFiles,
        agentProvider = provider,
        launchMode = item.profile.launchMode,
      ),
      closeDialog = context.closeDialog,
      item = item,
      setActiveVcsMergeLaunchProfileId = setActiveVcsMergeLaunchProfileId,
      startSession = startSession,
    )
  }

  private fun resolveAndQuickLaunch(dataContext: DataContext): QuickLaunchResult {
    val context = resolveContext(dataContext) ?: return QuickLaunchResult.NoContext
    val menuModel = buildProviderMenuModel(context.project)
    val selection = resolveVcsLaunchProfileSelection(menuModel, context.project)
    val enabledProfileItems = enabledProfileItems(selection.profiles)
    if (enabledProfileItems.isEmpty()) {
      Messages.showErrorDialog(
        context.project,
        AgentVcsMergeBundle.message("merge.agent.resolve.no.providers"),
        AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.title"),
      )
      return QuickLaunchResult.NoProvidersShown
    }

    val quickStartItem = selection.quickStartItem ?: return QuickLaunchResult.NoProvidersShown
    launchResolution(context, quickStartItem)
    return QuickLaunchResult.Launched
  }

  private fun resolveContext(dataContext: DataContext): ResolveWithAgentContext? {
    val directContext = MergeResolveActionContext.KEY.getData(dataContext) ?: return null
    if (!directContext.isContextValid()) return null
    return ResolveWithAgentContext(
      project = directContext.project,
      selectionHintFiles = directContext.selectionHintFiles,
      closeDialog = directContext::closeSourceUi,
    )
  }

  private fun isMergeDialogActionPlace(place: String): Boolean {
    return place == ONE_SHOT_DIALOG_ACTION_PLACE || place == ITERATIVE_DIALOG_ACTION_PLACE
  }

  private fun buildProviderMenuModel(project: Project): AgentSessionProviderMenuModel {
    return buildAgentSessionLaunchProfileMenuModel(allProviders(), project)
  }

  private fun resolveVcsLaunchProfileSelection(
    menuModel: AgentSessionProviderMenuModel,
    project: Project,
  ): AgentSessionLaunchProfileSelection {
    return resolveAgentSessionLaunchProfileSelection(
      menuModel = menuModel,
      userProfiles = userLaunchProfiles(),
      preferredProfileId = activeVcsMergeLaunchProfileId(),
      project = project,
      fallbackProfileIds = listOf(builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)),
      quickStartItemFilter = { item -> item.menuItem.isEnabled },
    )
  }

  private fun menuItems(menuModel: AgentSessionProviderMenuModel): List<AgentSessionProviderMenuItem> {
    return menuModel.standardItems + menuModel.yoloItems
  }

  private fun enabledProfileItems(profileItems: List<AgentSessionLaunchProfileMenuItem>): List<AgentSessionLaunchProfileMenuItem> {
    return profileItems.filter { item -> item.menuItem.isEnabled }
  }

  private fun disabledDescription(item: AgentSessionProviderMenuItem): @Nls String? {
    if (item.isEnabled) {
      return null
    }
    return providerMenuItemDisabledReason(item)
  }

  private inner class ResolveWithAgentOptionButton(
    place: String,
  ) : JBOptionButton(null, null) {
    private val primaryAction = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        performButtonAction()
      }
    }

    init {
      isFocusable = false
      addSeparator = false
      hideDisabledOptions = false
      optionTooltipText = getDefaultTooltip()
      putClientProperty(PLACE, place)
      action = primaryAction
    }

    fun updateFromPresentation(presentation: Presentation) {
      val text = presentation.getText(true)
      val description = presentation.description
      primaryAction.putValue(Action.NAME, text)
      primaryAction.putValue(Action.SMALL_ICON, presentation.icon)
      primaryAction.putValue(Action.SHORT_DESCRIPTION, description)
      primaryAction.putValue(Action.MNEMONIC_KEY, presentation.mnemonic)
      primaryAction.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, presentation.displayedMnemonicIndex)
      primaryAction.isEnabled = presentation.isEnabled
      isVisible = presentation.isVisible
      setToolTipText(description?.let(HtmlChunk::text))
      setOptions(buildOptionActions())
      minimumSize = preferredSize
    }

    private fun performButtonAction() {
      val dataContext = DataManager.getInstance().getDataContext(this)
      when (resolveAndQuickLaunch(dataContext)) {
        QuickLaunchResult.NoContext,
        QuickLaunchResult.NoProvidersShown,
        QuickLaunchResult.Launched,
          -> return
      }
    }

    private fun buildOptionActions(): List<AnAction>? {
      val dataContext = DataManager.getInstance().getDataContext(this)
      val context = resolveContext(dataContext)
      val project = context?.project ?: latestUpdateProject.get() ?: return null
      val selection = resolveVcsLaunchProfileSelection(buildProviderMenuModel(project), project)
      val enabledProfileItems = enabledProfileItems(selection.profiles)
      if (enabledProfileItems.size <= 1) {
        return null
      }

      return buildAgentSessionLaunchProfileMenuActions(
        path = project.basePath.orEmpty(),
        project = project,
        selection = selection,
        entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
        includeManageAction = false,
        createNewSession = { _, profile, _, _ ->
          val resolvedContext =
            context ?: resolveContext(DataManager.getInstance().getDataContext(this)) ?: return@buildAgentSessionLaunchProfileMenuActions
          val item = selection.profiles.firstOrNull { profileItem -> profileItem.profile.id == profile.id }
                     ?: return@buildAgentSessionLaunchProfileMenuActions
          launchResolution(resolvedContext, item)
        },
      ).toList()
    }
  }
}

internal fun launchAgentMergeResolution(
  project: Project,
  request: AgentVcsMergeLaunchRequest,
  closeDialog: (() -> Unit)?,
  item: AgentSessionLaunchProfileMenuItem,
  setActiveVcsMergeLaunchProfileId: (String?) -> Unit = { profileId ->
    service<AgentSessionLaunchProfileStateService>().setActiveVcsMergeLaunchProfileId(profileId)
  },
  startSession: (Project, AgentVcsMergeLaunchRequest) -> Unit = ::startAgentMergeSession,
) {
  closeDialog?.invoke()
  val provider = AgentSessionProvider.fromOrNull(item.profile.providerId) ?: return
  setActiveVcsMergeLaunchProfileId(item.profile.id)
  startSession(project, request.copy(
    agentProvider = provider,
    launchMode = item.profile.launchMode,
    launchProfileId = item.profile.id,
    launchTargetId = item.profile.launchTargetId,
    generationSettings = generationSettingsForPlanMode(
      generationSettings = item.profile.generationSettings,
      startInPlanMode = false,
    ),
  ))
}

private fun startAgentMergeSession(project: Project, request: AgentVcsMergeLaunchRequest) {
  project.service<AgentVcsMergeSessionService>().startOrFocusSession(request)
}
