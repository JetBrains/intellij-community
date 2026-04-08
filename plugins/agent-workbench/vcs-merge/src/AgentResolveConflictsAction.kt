// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.buildAgentSessionProviderMenuActions
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.providerIconWithMode
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.merge.MergeDialogContext
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

private data class ResolveWithAgentContext(
  val project: Project,
  val request: AgentVcsMergeLaunchRequest,
  val closeDialog: (() -> Unit)?,
)

private sealed interface QuickLaunchResult {
  data object NoContext : QuickLaunchResult
  data object NoProvidersShown : QuickLaunchResult
  data object Launched : QuickLaunchResult
  data class ShowPopup(
    val context: ResolveWithAgentContext,
    val menuModel: AgentSessionProviderMenuModel,
  ) : QuickLaunchResult
}

private const val ONE_SHOT_DIALOG_ACTION_PLACE: String = "Merge.OneShotDialog"
private const val PROVIDER_POPUP_PLACE: String = "Merge.ResolveWithAgent.ProviderPopup"

internal class AgentResolveConflictsAction @JvmOverloads constructor(
  private val allProviders: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionUiPreferencesStateService>().getLastUsedVcsMergeProvider() },
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode? = { service<AgentSessionUiPreferencesStateService>().getLastUsedVcsMergeLaunchMode() },
) : DumbAwareAction(), CustomComponentAction {
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

    val actionModel = buildProviderActionModel()
    val enabledItems = enabledItems(actionModel.menuModel)
    val rememberedQuickStartItem = rememberedQuickStartItem(actionModel.menuModel)

    presentation.isEnabled = context.request.files.isNotEmpty() && enabledItems.isNotEmpty()
    presentation.description = when {
      presentation.isEnabled -> templatePresentation.description
      menuItems(actionModel.menuModel).isEmpty() -> AgentVcsMergeBundle.message("merge.agent.resolve.no.providers")
      else -> menuItems(actionModel.menuModel).firstNotNullOfOrNull(::disabledDescription)
              ?: AgentVcsMergeBundle.message("merge.agent.resolve.no.providers")
    }
    presentation.icon = rememberedQuickStartItem?.let { providerIconWithMode(it.bridge.provider, it.mode) }
                        ?: enabledItems.firstOrNull()?.let { providerIconWithMode(it.bridge.provider, it.mode) }
                        ?: AllIcons.Actions.InlayGear
  }

  override fun actionPerformed(e: AnActionEvent) {
    performAction(
      dataContext = e.dataContext,
      popupAnchor = resolvePopupAnchor(e),
    )
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    if (place != ONE_SHOT_DIALOG_ACTION_PLACE) {
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

  private fun performAction(
    dataContext: DataContext,
    popupAnchor: JComponent?,
  ): Boolean {
    return when (val result = resolveAndQuickLaunch(dataContext)) {
      QuickLaunchResult.NoContext, QuickLaunchResult.NoProvidersShown -> false
      QuickLaunchResult.Launched -> true
      is QuickLaunchResult.ShowPopup -> {
        showProviderPopup(result.context, result.menuModel, dataContext, popupAnchor)
        true
      }
    }
  }

  private fun showProviderPopup(
    context: ResolveWithAgentContext,
    menuModel: AgentSessionProviderMenuModel,
    dataContext: DataContext,
    popupAnchor: JComponent?,
  ) {
    val anchor = popupAnchor ?: (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) as? JComponent) ?: return
    val popupMenu = createProviderPopup(context, menuModel, dataContext)
    popupMenu.setTargetComponent(anchor)
    popupMenu.component.show(anchor, 0, anchor.height)
  }

  private fun createProviderPopup(
    context: ResolveWithAgentContext,
    menuModel: AgentSessionProviderMenuModel,
    dataContext: DataContext,
  ): ActionPopupMenu {
    val popupGroup = DefaultActionGroup().apply {
      buildAgentSessionProviderMenuActions(menuModel) { item ->
        launchResolution(context, item)
      }.forEach(::add)
    }
    return ActionManager.getInstance().createActionPopupMenu(PROVIDER_POPUP_PLACE, popupGroup).apply {
      setDataContext { dataContext }
    }
  }

  private fun launchResolution(context: ResolveWithAgentContext, item: AgentSessionProviderMenuItem) {
    launchAgentMergeResolution(
      project = context.project,
      request = context.request,
      closeDialog = context.closeDialog,
      item = item,
    )
  }

  private fun resolveAndQuickLaunch(dataContext: DataContext): QuickLaunchResult {
    val context = resolveContext(dataContext) ?: return QuickLaunchResult.NoContext
    val menuModel = buildProviderActionModel().menuModel
    val enabledItems = enabledItems(menuModel)
    if (enabledItems.isEmpty()) {
      Messages.showErrorDialog(
        context.project,
        AgentVcsMergeBundle.message("merge.agent.resolve.no.providers"),
        AgentVcsMergeBundle.message("merge.agent.resolve.launch.failed.title"),
      )
      return QuickLaunchResult.NoProvidersShown
    }

    if (enabledItems.size == 1) {
      launchResolution(context, enabledItems.first())
      return QuickLaunchResult.Launched
    }

    val quickStartItem = rememberedQuickStartItem(menuModel)
    if (quickStartItem != null) {
      launchResolution(context, quickStartItem)
      return QuickLaunchResult.Launched
    }

    return QuickLaunchResult.ShowPopup(context, menuModel)
  }

  private fun resolveContext(dataContext: DataContext): ResolveWithAgentContext? {
    val mergeDialogContext = MergeDialogContext.KEY.getData(dataContext)
    if (mergeDialogContext != null) {
      return ResolveWithAgentContext(
        project = mergeDialogContext.project,
        request = AgentVcsMergeLaunchRequest(
          files = mergeDialogContext.unresolvedFiles,
          mergeProvider = mergeDialogContext.mergeProvider,
          mergeDialogCustomizer = mergeDialogContext.mergeDialogCustomizer,
          agentProvider = AgentSessionProvider.CODEX,
          launchMode = AgentSessionLaunchMode.STANDARD,
        ),
        closeDialog = mergeDialogContext.takeIf(MergeDialogContext::isModalDialog)?.let { it::closeDialogForAgentHandoff },
      )
    }

    val directContext = MergeResolveWithAgentContext.KEY.getData(dataContext) ?: return null
    val mergeRequest = createDirectMergeRequest(directContext) ?: return null
    return ResolveWithAgentContext(
      project = directContext.project,
      request = mergeRequest,
      closeDialog = directContext::closeDialogForAgentHandoff,
    )
  }

  private fun createDirectMergeRequest(context: MergeResolveWithAgentContext): AgentVcsMergeLaunchRequest? {
    val files = context.files
    if (files.isEmpty()) return null

    val mergeProvider = ProjectLevelVcsManager.getInstance(context.project)
                          .let { vcsManager -> files.firstNotNullOfOrNull { file -> vcsManager.getVcsFor(file)?.mergeProvider } }
                        ?: return null

    return AgentVcsMergeLaunchRequest(
      files = files,
      mergeProvider = mergeProvider,
      mergeDialogCustomizer = mergeProvider.createDefaultMergeDialogCustomizer(),
      agentProvider = AgentSessionProvider.CODEX,
      launchMode = AgentSessionLaunchMode.STANDARD,
    )
  }

  private fun buildProviderActionModel(): AgentSessionProviderActionModel {
    return buildAgentSessionProviderActionModel(
      bridges = allProviders(),
      lastUsedProvider = lastUsedProvider(),
      lastUsedLaunchMode = lastUsedLaunchMode(),
    )
  }

  private fun rememberedQuickStartItem(menuModel: AgentSessionProviderMenuModel): AgentSessionProviderMenuItem? {
    val provider = lastUsedProvider() ?: return null
    val launchMode = lastUsedLaunchMode() ?: return null
    return menuItems(menuModel).firstOrNull { item ->
      item.isEnabled && item.bridge.provider == provider && item.mode == launchMode
    }
  }

  private fun menuItems(menuModel: AgentSessionProviderMenuModel): List<AgentSessionProviderMenuItem> {
    return menuModel.standardItems + menuModel.yoloItems
  }

  private fun enabledItems(menuModel: AgentSessionProviderMenuModel): List<AgentSessionProviderMenuItem> {
    return menuItems(menuModel).filter(AgentSessionProviderMenuItem::isEnabled)
  }

  private fun disabledDescription(item: AgentSessionProviderMenuItem): @Nls String? {
    val reasonKey = item.disabledReasonKey
    if (reasonKey != null) {
      return AgentSessionsBundle.message(reasonKey)
    }
    if (item.isEnabled) {
      return null
    }
    return AgentSessionsBundle.message("toolwindow.action.new.session.unavailable", providerDisplayName(item.bridge))
  }

  private fun providerDisplayName(descriptor: AgentSessionProviderDescriptor): @NlsSafe String {
    return runCatching { AgentSessionsBundle.message(descriptor.displayNameKey) }.getOrDefault(descriptor.displayNameFallback)
  }

  private fun resolvePopupAnchor(e: AnActionEvent): JComponent? {
    return (e.inputEvent?.component as? JComponent)
           ?: (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent)
  }

  private inner class ResolveWithAgentOptionButton(
    place: String,
  ) : JBOptionButton(null, null) {
    init {
      isFocusable = false
      addSeparator = false
      hideDisabledOptions = false
      optionTooltipText = getDefaultTooltip()
      putClientProperty(PLACE, place)
      action = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          performButtonAction()
        }
      }
    }

    fun updateFromPresentation(presentation: Presentation) {
      isVisible = presentation.isVisible
      text = presentation.getText(true)
      icon = presentation.icon
      isEnabled = presentation.isEnabled
      setToolTipText(presentation.description?.let(HtmlChunk::text))
      mnemonic = presentation.mnemonic
      displayedMnemonicIndex = presentation.displayedMnemonicIndex
      setOptions(buildOptionActions())
    }

    private fun performButtonAction() {
      val dataContext = DataManager.getInstance().getDataContext(this)
      when (resolveAndQuickLaunch(dataContext)) {
        QuickLaunchResult.NoContext,
        QuickLaunchResult.NoProvidersShown,
        QuickLaunchResult.Launched,
          -> return
        is QuickLaunchResult.ShowPopup -> showPopup()
      }
    }

    private fun buildOptionActions(): List<AnAction>? {
      val menuModel = buildProviderActionModel().menuModel
      if (enabledItems(menuModel).size <= 1) {
        return null
      }

      return buildAgentSessionProviderMenuActions(menuModel) { item ->
        val dataContext = DataManager.getInstance().getDataContext(this)
        val context = resolveContext(dataContext) ?: return@buildAgentSessionProviderMenuActions
        launchResolution(context, item)
      }.toList()
    }
  }
}

internal fun launchAgentMergeResolution(
  project: Project,
  request: AgentVcsMergeLaunchRequest,
  closeDialog: (() -> Unit)?,
  item: AgentSessionProviderMenuItem,
  startSession: (Project, AgentVcsMergeLaunchRequest) -> Unit = ::startAgentMergeSession,
) {
  closeDialog?.invoke()
  startSession(project, request.copy(agentProvider = item.bridge.provider, launchMode = item.mode))
}

private fun startAgentMergeSession(project: Project, request: AgentVcsMergeLaunchRequest) {
  project.service<AgentVcsMergeSessionService>().startOrFocusSession(request)
}
