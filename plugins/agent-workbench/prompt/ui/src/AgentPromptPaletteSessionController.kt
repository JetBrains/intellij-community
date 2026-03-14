// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INITIAL_TEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.Nls
import javax.swing.JPanel
import javax.swing.event.ChangeListener

internal class AgentPromptPaletteSessionController(
  private val project: Project,
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: AgentPromptTextField,
  private val view: AgentPromptPaletteView,
  private val contextChips: AgentPromptContextChipsComponent,
  private val providerSelector: AgentPromptProviderSelector,
  private val existingTaskController: AgentPromptExistingTaskController,
  private val suggestionController: AgentPromptSuggestionController,
  private val contextResolverService: AgentPromptContextResolverService,
  private val uiStateService: AgentPromptUiSessionStateService,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val closePopup: () -> Unit,
  private val isPopupActive: () -> Boolean,
  private val movePopupToFitScreen: () -> Unit,
) {
  private val contextState = AgentPromptPaletteContextState()
  private val draftState = AgentPromptPaletteDraftState()
  private val launchState = AgentPromptPaletteLaunchState()

  private val contextController: AgentPromptPaletteContextController
  private val draftController: AgentPromptPaletteDraftController
  private val submitController: AgentPromptPaletteSubmitController

  init {
    lateinit var submitControllerRef: AgentPromptPaletteSubmitController
    lateinit var draftControllerRef: AgentPromptPaletteDraftController

    contextController = AgentPromptPaletteContextController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      view = view,
      contextResolverService = contextResolverService,
      contextChips = contextChips,
      launcherProvider = launcherProvider,
      state = contextState,
      resolveWorkingProjectPath = { submitControllerRef.resolveWorkingProjectPath() },
      resolveContextProjectBasePath = { submitControllerRef.resolveContextProjectBasePath() },
      showError = ::showError,
      onContextChanged = ::handleContextChanged,
      onExtensionTabRemoved = { taskKey -> draftControllerRef.removeTaskDraft(taskKey) },
      setTargetMode = ::setTargetMode,
    )

    draftController = AgentPromptPaletteDraftController(
      invocationData = invocationData,
      promptArea = promptArea,
      tabbedPane = view.tabbedPane,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      uiStateService = uiStateService,
      launcherProvider = launcherProvider,
      contextState = contextState,
      draftState = draftState,
      refreshContextEntries = contextController::refreshContextEntries,
      resolveExtensionTabs = contextController::resolveExtensionTabs,
      reloadExistingTasks = ::reloadExistingTasks,
      updateProviderOptionsVisibility = ::updateProviderOptionsVisibility,
      setTargetMode = ::setTargetMode,
      resolveTaskKey = ::resolveTaskKey,
    )
    draftControllerRef = draftController

    submitController = AgentPromptPaletteSubmitController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      launcherProvider = launcherProvider,
      launchState = launchState,
      currentTargetMode = ::currentTargetMode,
      activeExtensionTab = { contextState.activeExtensionTab },
      buildVisibleContextEntries = { contextController.buildVisibleContextEntries() },
      resolveContextSelection = contextController::resolveContextSelection,
      onWorkingProjectPathSelected = { _ -> handleWorkingProjectPathSelected() },
      onSubmitBlocked = ::showError,
      onSubmitSucceeded = ::closeAfterSuccessfulSubmit,
    )
    submitControllerRef = submitController
  }

  fun initialize() {
    contextController.configureAddContextButton()
    refreshProviders()
    contextController.loadInitialContext()
    contextController.resolveExtensionTabs()

    val draft = draftController.restoreDraft()
    draftController.restoreTaskDrafts(draft)

    if (invocationData.attributes[com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY] == true) {
      contextController.selectAutoSelectExtensionTab()
    }
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    val initialText = invocationData.dataContextOrNull()?.getData(AGENT_PROMPT_INITIAL_TEXT_DATA_KEY)
    draftController.overrideInitialTextIfProvided(initialText)
    draftController.loadPromptTextForSelectedTab()
    clearStatus()
    updateTargetModeUi()
    updateSendAvailability()
  }

  fun installHandlers() {
    contextController.installImagePasteHandler()

    installConfirmActionOnEnter(view.existingTaskList) {
      submitController.submit()
      true
    }

    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = submitController::canSubmit,
      isTabQueueEnabled = {
        isTabQueueShortcutEnabled(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = view.tabbedPane.selectedIndex in 0 until view.tabbedPane.tabCount - 1,
        )
      },
      onSubmit = submitController::submit,
      onTabFocusTransfer = { selectAdjacentPromptTab(view.tabbedPane, 1) },
      onTabBackwardFocusTransfer = { selectAdjacentPromptTab(view.tabbedPane, -1) },
    )

    view.tabbedPane.addChangeListener(ChangeListener {
      handleTabSwitch()
      updateTargetModeUi()
      updateSendAvailability()
      movePopupToFitScreen()
    })

    promptArea.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        onPromptChanged()
      }
    })
  }

  fun onPopupClosed() {
    existingTaskController.dispose()
    suggestionController.dispose()
    draftController.saveProviderPreferences()
    if (launchState.clearDraftOnClose) {
      uiStateService.clearDraft()
    }
    else {
      draftController.saveDraft(currentTargetMode())
    }
  }

  fun showProviderChooser() {
    providerSelector.showChooser(onUnavailable = ::showError) {
      if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
        existingTaskController.clearSelection()
        reloadExistingTasks()
      }
      updateProviderOptionsVisibility()
      updateSendAvailability()
      refreshFooterHintForCurrentState()
    }
  }

  fun onExistingTaskSelected(selected: ThreadEntry) {
    existingTaskController.onUserSelected(selected)
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  fun onExistingTaskStateChanged() {
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  fun removeContextEntry(entry: ContextEntry) {
    contextController.removeContextEntry(entry)
  }

  fun applySuggestedPrompt(candidate: AgentPromptSuggestionCandidate) {
    draftController.applySuggestedPrompt(candidate.promptText)
    IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
  }

  private fun handleTabSwitch() {
    draftController.savePromptTextForSelectedTab()
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    draftController.loadPromptTextForSelectedTab()
  }

  private fun onPromptChanged() {
    draftController.onPromptChanged()
    updateSendAvailability()
    clearStatus()
  }

  private fun refreshProviders() {
    providerSelector.refresh()
    updateProviderOptionsVisibility()
  }

  private fun updateProviderOptionsVisibility() {
    val providerOptionsPanel = checkNotNull(view.providerOptionsPanel)
    providerOptionsPanel.isVisible = contextState.activeExtensionTab == null && providerOptionsPanel.componentCount > 0
    providerOptionsPanel.revalidate()
    providerOptionsPanel.repaint()
  }

  private fun reloadExistingTasks() {
    val launcher = launcherProvider()
    existingTaskController.reload(
      selectedProviderEntry = providerSelector.selectedProvider,
      launcher = launcher,
      projectPath = submitController.resolveWorkingProjectPath(),
      isPopupActive = isPopupActive,
    )
  }

  private fun updateTargetModeUi() {
    val isExtensionTab = contextState.activeExtensionTab != null
    val mode = currentTargetMode()
    view.existingTaskScrollPane.isVisible = !isExtensionTab && mode == PromptTargetMode.EXISTING_TASK
    if (!isExtensionTab && mode == PromptTargetMode.EXISTING_TASK && !existingTaskController.hasLoadedEntries()) {
      reloadExistingTasks()
    }
    updateProviderOptionsVisibility()
    refreshSuggestions()
    refreshFooterHintForCurrentState()
  }

  private fun updateSendAvailability() {
    submitController.updateSendAvailability()
  }

  private fun refreshSuggestions() {
    if (contextState.activeExtensionTab != null) {
      suggestionController.clearSuggestions()
      return
    }

    suggestionController.reloadSuggestions(
      AgentPromptSuggestionRequest(
        project = project,
        projectPath = submitController.resolveWorkingProjectPath(),
        targetModeId = currentTargetMode().name,
        contextItems = contextController.buildVisibleContextEntries().map(ContextEntry::item),
      )
    )
  }

  private fun resolveTaskKey(panel: JPanel?): String? {
    if (panel == null) return null
    val mode = panel.getClientProperty("targetMode") as? PromptTargetMode
    if (mode != null) return mode.name
    return contextState.activeExtensionTabs.firstOrNull { it.tabPanel === panel }?.taskKey
  }

  private fun currentTargetMode(): PromptTargetMode {
    val selectedComponent = view.tabbedPane.selectedComponent as? JPanel ?: return PromptTargetMode.NEW_TASK
    return selectedComponent.getClientProperty("targetMode") as? PromptTargetMode ?: PromptTargetMode.NEW_TASK
  }

  private fun setTargetMode(mode: PromptTargetMode) {
    val index = findTabIndexForMode(mode) ?: return
    view.tabbedPane.selectedIndex = index
  }

  private fun findTabIndexForMode(mode: PromptTargetMode): Int? {
    for (i in 0 until view.tabbedPane.tabCount) {
      val component = view.tabbedPane.getComponentAt(i) as? JPanel ?: continue
      if (component.getClientProperty("targetMode") == mode) {
        return i
      }
    }
    return null
  }

  private fun clearStatus() {
    val extensionTab = contextState.activeExtensionTab
    view.footerLabel.text = if (extensionTab != null) {
      extensionTab.extension.getFooterHint() ?: AgentPromptBundle.message("popup.footer.hint.default.tab")
    }
    else {
      AgentPromptBundle.message(
        resolveDefaultFooterHintMessageKey(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = view.tabbedPane.selectedIndex in 0 until view.tabbedPane.tabCount - 1,
        )
      )
    }
    view.footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
  }

  private fun refreshFooterHintForCurrentState() {
    if (shouldShowExistingTaskSelectionHint(
        targetMode = currentTargetMode(),
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        selectedProvider = providerSelector.selectedProvider?.bridge,
      )) {
      showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
      return
    }

    clearStatus()
  }

  private fun handleContextChanged(message: @Nls String) {
    updateTargetModeUi()
    updateSendAvailability()
    showInfo(message)
  }

  private fun handleWorkingProjectPathSelected() {
    contextController.refreshContextEntries()
    contextController.resolveExtensionTabs()
    updateTargetModeUi()
    if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
      existingTaskController.clearSelection()
      reloadExistingTasks()
    }
    updateSendAvailability()
  }

  private fun closeAfterSuccessfulSubmit() {
    closePopup()
  }

  private fun showError(message: @Nls String) {
    view.footerLabel.foreground = NamedColorUtil.getErrorForeground()
    view.footerLabel.text = message
  }

  private fun showInfo(message: @Nls String) {
    view.footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
    view.footerLabel.text = message
  }
}
