// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INITIAL_TEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensionContext
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
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
  private val popupScope: CoroutineScope,
) {
  private val contextState = AgentPromptPaletteContextState()
  private val draftState = AgentPromptPaletteDraftState()
  private val launchState = AgentPromptPaletteLaunchState()

  private val contextController: AgentPromptPaletteContextController
  private val draftController: AgentPromptPaletteDraftController
  private val submitController: AgentPromptPaletteSubmitController

  init {
    lateinit var submitControllerRef: AgentPromptPaletteSubmitController

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
      onExtensionTabRemoved = { taskKeyPrefix ->
        draftState.taskPromptStates.keys.removeAll { key ->
          AgentPromptExtensionDraftDecisions.matchesTaskKey(taskKeyPrefix, key)
        }
      },
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
      getContainerModeSelected = { view.containerModeCheckBox.isSelected },
      setContainerModeSelected = { view.containerModeCheckBox.isSelected = it },
    )
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
      onPromptSubmitted = uiStateService::saveSubmittedPromptHistoryEntry,
      isContainerModeSelected = { view.containerModeCheckBox.isSelected },
    )
    submitControllerRef = submitController
  }

  fun initialize(initialAddContextRequest: AgentPromptAddContextRequest? = null) {
    contextController.configureAddContextButton()
    refreshProviders()
    contextController.loadInitialContext(initialAddContextRequest?.contextItems)
    contextController.resolveExtensionTabs()

    val draft = draftController.restoreDraft(restoreContextSnapshot = initialAddContextRequest == null)
    draftController.restoreTaskDrafts(draft)
    refreshExtensionTaskDraftsFromContext()

    if (invocationData.attributes[com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY] == true) {
      contextController.selectAutoSelectExtensionTab()
    }
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    val initialText = invocationData.dataContextOrNull()?.getData(AGENT_PROMPT_INITIAL_TEXT_DATA_KEY)
    draftController.overrideInitialTextIfProvided(initialText)
    if (initialAddContextRequest != null) {
      applyInitialAddContextTarget(initialAddContextRequest.target)
      contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    }
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
        autoPopupClaudeSlashCompletionIfNeeded(event)
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

  fun showPromptHistoryChooser() {
    val historyEntries = uiStateService.loadPromptHistory()
    if (historyEntries.isEmpty()) {
      showInfo(AgentPromptBundle.message("popup.history.empty"))
      return
    }

    val snapshot = draftController.snapshotPrompt()
    var chosenEntry: AgentPromptHistoryEntry? = null
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(historyEntries)
      .setTitle(AgentPromptBundle.message("popup.history.title"))
      .setVisibleRowCount(historyEntries.size.coerceAtMost(7))
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setNamerForFiltering { entry -> entry.promptText }
      .setItemSelectedCallback { entry ->
        entry?.let { draftController.previewPromptText(it.promptText) }
      }
      .setItemChosenCallback { entry ->
        chosenEntry = entry
        draftController.replacePromptTextFromChooser(entry.promptText)
        IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
      }
      .setRenderer(PromptHistoryCellRenderer())
      .addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          ApplicationManager.getApplication().invokeLater {
            if (chosenEntry == null && !project.isDisposed) {
              draftController.restorePromptSnapshot(snapshot)
              IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
            }
          }
        }
      })
      .setAutoPackHeightOnFiltering(false)
      .createPopup()
      .showUnderneathOf(view.historyIconLabel)
  }

  fun showReusableSourceChooser() {
    popupScope.launch {
      val sourceEntries = collectReusablePromptSourceEntries(
        selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
        workingProjectPaths = reusableSourceProjectPaths(),
        launcher = launcherProvider(),
        resolvedProjectPath = submitController.resolveWorkingProjectPath(),
      )
      withContext(Dispatchers.EDT) {
        if (sourceEntries.isEmpty()) {
          showInfo(AgentPromptBundle.message("popup.source.empty"))
        }
        else {
          showReusableSourceChooser(sourceEntries)
        }
      }
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

  fun resolveWorkingProjectPath(): String? = submitController.resolveWorkingProjectPath()

  fun applyAddContextRequest(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult {
    val result = contextController.addExternalContextItems(request.contextItems)
    when (result) {
      AgentPromptAddContextApplyResult.ADDED -> handleContextChanged(AgentPromptBundle.message("popup.status.context.added"))
      AgentPromptAddContextApplyResult.ALREADY_ADDED -> showInfo(AgentPromptBundle.message("popup.status.context.already.added"))
    }
    return result
  }

  private fun handleTabSwitch() {
    draftController.savePromptTextForSelectedTab()
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    draftController.loadPromptTextForSelectedTab()
  }

  private fun showReusableSourceChooser(sourceEntries: List<AgentPromptReusableSourceEntry>) {
    val snapshot = draftController.snapshotPrompt()
    var chosenEntry: AgentPromptReusableSourceEntry? = null
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(sourceEntries)
      .setTitle(AgentPromptBundle.message("popup.source.title"))
      .setVisibleRowCount(sourceEntries.size.coerceAtMost(9))
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setNamerForFiltering { entry -> listOfNotNull(entry.label, entry.description, entry.sourcePath).joinToString("\n") }
      .setItemSelectedCallback { entry ->
        entry?.let { draftController.previewPromptText(it.insertText) }
      }
      .setItemChosenCallback { entry ->
        chosenEntry = entry
        draftController.replacePromptTextFromChooser(entry.insertText)
        IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
      }
      .setRenderer(ReusableSourceCellRenderer())
      .addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          ApplicationManager.getApplication().invokeLater {
            if (chosenEntry == null && !project.isDisposed) {
              draftController.restorePromptSnapshot(snapshot)
              IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
            }
          }
        }
      })
      .setAutoPackHeightOnFiltering(false)
      .createPopup()
      .showUnderneathOf(view.sourceIconLabel)
  }

  private fun reusableSourceProjectPaths(): List<String> {
    val sourceProjectBasePath = launcherProvider()
      ?.resolveSourceProject(invocationData)
      ?.basePath
    return resolveClaudeSlashCompletionProjectPaths(
      workingProjectPath = submitController.resolveWorkingProjectPath(),
      sourceProjectBasePath = sourceProjectBasePath,
      projectBasePath = project.basePath,
    )
  }

  private fun onPromptChanged() {
    draftController.onPromptChanged()
    updateSendAvailability()
    clearStatus()
  }

  private fun autoPopupClaudeSlashCompletionIfNeeded(event: DocumentEvent) {
    val editor = promptArea.editor ?: return
    if (!editor.contentComponent.hasFocus()) {
      return
    }
    if (LookupManager.getActiveLookup(editor) != null) {
      return
    }

    val documentText = event.document.immutableCharSequence.toString()
    val sourceProjectBasePath = launcherProvider()
      ?.resolveSourceProject(invocationData)
      ?.basePath
    if (!shouldAutoPopupClaudeSlashCompletion(
        selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
        workingProjectPaths = resolveClaudeSlashCompletionProjectPaths(
          workingProjectPath = submitController.resolveWorkingProjectPath(),
          sourceProjectBasePath = sourceProjectBasePath,
          projectBasePath = project.basePath,
        ),
        text = documentText,
        offsetAfterChange = event.offset + event.newLength,
        insertedFragment = event.newFragment,
      )
    ) {
      return
    }

    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed || editor.isDisposed || !editor.contentComponent.hasFocus()) {
        return@invokeLater
      }
      if (LookupManager.getActiveLookup(editor) != null) {
        return@invokeLater
      }

      CodeCompletionHandlerBase(CompletionType.BASIC, false, true, true).invokeCompletion(project, editor, 1)
    }
  }

  private fun refreshProviders() {
    providerSelector.refresh()
    updateProviderOptionsVisibility()
  }

  private fun applyInitialAddContextTarget(target: AgentPromptAddContextTargetCandidate?) {
    if (target == null) {
      existingTaskController.clearSelection()
      setTargetMode(PromptTargetMode.NEW_TASK)
      return
    }

    launchState.selectedWorkingProjectPath = target.projectPath
    providerSelector.selectProvider(target.provider, target.launchMode)
    existingTaskController.selectedExistingTaskId = target.threadId
    setTargetMode(PromptTargetMode.EXISTING_TASK)
    reloadExistingTasks()
  }

  private fun updateProviderOptionsVisibility() {
    val providerOptionsPanel = checkNotNull(view.providerOptionsPanel)
    providerOptionsPanel.isVisible = contextState.activeExtensionTab == null && providerOptionsPanel.componentCount > 0
    providerOptionsPanel.revalidate()
    providerOptionsPanel.repaint()

    // Container mode only supported for providers with --disallowedTools (currently Claude only).
    // Not applicable on AI Review (and other extension tabs) — they use a different launch path.
    val supportsContainer = providerSelector.selectedProvider?.bridge?.provider == AgentSessionProvider.CLAUDE
    val isExtensionTab = contextState.activeExtensionTab != null
    view.containerModeCheckBox.isVisible = supportsContainer && !isExtensionTab
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
    view.rootPanel.revalidate()
    movePopupToFitScreen()
    if (!isExtensionTab && mode == PromptTargetMode.EXISTING_TASK) {
      if (!existingTaskController.hasLoadedEntries()) {
        reloadExistingTasks()
      }
      refreshPreselection()
    }
    updateProviderOptionsVisibility()
    refreshSuggestions()
    refreshFooterHintForCurrentState()
  }

  private fun refreshPreselection() {
    popupScope.launch {
      val preferredId = resolvePreferredThreadId() ?: return@launch
      existingTaskController.setPreselection(preferredId)
    }
  }

  private suspend fun resolvePreferredThreadId(): String? {
    val launcher = launcherProvider() ?: return null
    val projectPath = submitController.resolveWorkingProjectPath() ?: return null
    val provider = providerSelector.selectedProvider?.bridge?.provider ?: return null
    return runCatching { launcher.listAddContextTargetCandidates(projectPath) }
      .getOrDefault(emptyList())
      .firstOrNull { candidate -> candidate.selected && candidate.provider == provider }
      ?.threadId
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
    return contextState.activeExtensionTabs.firstOrNull { it.tabPanel === panel }?.let(::resolveExtensionTaskKey)
  }

  private fun currentContextItems(): List<AgentPromptContextItem> {
    return contextController.buildVisibleContextEntries().map(ContextEntry::item)
  }

  private fun resolveExtensionTaskKey(
    entry: AgentPromptPaletteExtensionTab,
    contextItems: List<AgentPromptContextItem> = currentContextItems(),
  ): String {
    return AgentPromptPaletteExtensionContext.withContextItems(project, contextItems) {
      AgentPromptExtensionDraftDecisions.taskKey(entry.taskKeyPrefix, entry.extension.getInitialPrompt(project)?.kind)
    }
  }

  private fun refreshExtensionTaskDraftsFromContext() {
    val contextItems = currentContextItems()
    for (entry in contextState.activeExtensionTabs) {
      val taskKey = resolveExtensionTaskKey(entry, contextItems)
      val updatedState = AgentPromptPaletteExtensionContext.withContextItems(project, contextItems) {
        val initialPrompt = entry.extension.getInitialPrompt(project)
        val currentState = draftState.taskPromptStates[taskKey]
        if (currentState == null) {
          initialPrompt
            ?.let { restoredTaskPromptDraftState(it.content) }
        }
        else {
          synchronizeExtensionDraftState(currentState, initialPrompt?.kind) { prompt ->
            entry.extension.synchronizePrompt(project, prompt)
          }
        }
      }

      if (updatedState == null) {
        draftState.taskPromptStates.remove(taskKey)
      }
      else {
        draftState.taskPromptStates[taskKey] = updatedState
      }

      if (contextState.activeExtensionTab === entry) {
        draftState.activeTaskKey = taskKey
      }
    }

    if (contextState.activeExtensionTab != null) {
      val taskKey = draftState.activeTaskKey ?: return
      val updatedText = draftState.taskPromptStates[taskKey]?.liveText.orEmpty()
      if (promptArea.text != updatedText) {
        draftController.setPromptAreaTextProgrammatically(updatedText)
      }
    }
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
      )
    ) {
      showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
      return
    }

    clearStatus()
  }

  private fun handleContextChanged(message: @Nls String) {
    refreshExtensionTaskDraftsFromContext()
    updateTargetModeUi()
    updateSendAvailability()
    showInfo(message)
  }

  private fun handleWorkingProjectPathSelected() {
    contextController.refreshContextEntries()
    contextController.resolveExtensionTabs()
    refreshExtensionTaskDraftsFromContext()
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

@Suppress("HardCodedStringLiteral")
private class PromptHistoryCellRenderer : ColoredListCellRenderer<AgentPromptHistoryEntry>() {
  override fun customizeCellRenderer(
    list: JList<out AgentPromptHistoryEntry>,
    value: AgentPromptHistoryEntry?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    if (value == null) {
      return
    }
    append(compactPromptPreview(value.promptText), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val details = listOfNotNull(value.providerId, value.targetMode.name.lowercase().replace('_', ' ')).joinToString(" · ")
    if (details.isNotBlank()) {
      append("  $details", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }
}

@Suppress("HardCodedStringLiteral")
private class ReusableSourceCellRenderer : ColoredListCellRenderer<AgentPromptReusableSourceEntry>() {
  override fun customizeCellRenderer(
    list: JList<out AgentPromptReusableSourceEntry>,
    value: AgentPromptReusableSourceEntry?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    if (value == null) {
      return
    }
    append(value.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    append("  ${sourceKindLabel(value.kind)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    value.description?.takeIf(String::isNotBlank)?.let { description ->
      append("  ${StringUtil.first(description, 80, true)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }
}

private fun compactPromptPreview(promptText: String): String {
  val singleLine = promptText.replace('\n', ' ').trim()
  return StringUtil.first(singleLine, 100, true)
}

private fun sourceKindLabel(kind: AgentPromptReusableSourceKind): String {
  return when (kind) {
    AgentPromptReusableSourceKind.PROMPT_FILE -> AgentPromptBundle.message("popup.source.type.prompt.file")
    AgentPromptReusableSourceKind.COMMAND -> AgentPromptBundle.message("popup.source.type.command")
    AgentPromptReusableSourceKind.SKILL -> AgentPromptBundle.message("popup.source.type.skill")
  }
}
