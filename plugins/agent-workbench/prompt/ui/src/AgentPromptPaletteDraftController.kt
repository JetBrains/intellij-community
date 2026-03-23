// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.ui.EditorTextField
import javax.swing.JPanel
import javax.swing.JTabbedPane

internal class AgentPromptPaletteDraftController(
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: EditorTextField,
  private val tabbedPane: JTabbedPane,
  private val providerSelector: AgentPromptProviderSelector,
  private val existingTaskController: AgentPromptExistingTaskController,
  private val uiStateService: AgentPromptUiSessionStateService,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val contextState: AgentPromptPaletteContextState,
  private val draftState: AgentPromptPaletteDraftState,
  private val refreshContextEntries: () -> Unit,
  private val resolveExtensionTabs: () -> Unit,
  private val reloadExistingTasks: () -> Unit,
  private val updateProviderOptionsVisibility: () -> Unit,
  private val setTargetMode: (PromptTargetMode) -> Unit,
  private val resolveTaskKey: (JPanel?) -> String?,
) {
  fun restoreDraft(): AgentPromptUiDraft {
    val draft = uiStateService.loadDraft()
    val providerPrefs = launcherProvider()?.loadProviderPreferences() ?: AgentPromptLauncherBridge.ProviderPreferences()
    val contextRestoreSnapshot = uiStateService.loadContextRestoreSnapshot()
    val launcher = launcherProvider()

    setPromptAreaText(draft.promptText)
    val effectiveProviderOptions = providerPrefs.providerOptionsByProviderId.ifEmpty { draft.providerOptionsByProviderId }
    providerSelector.restoreProviderOptionSelections(effectiveProviderOptions)
    val persistedProvider = resolveRestoredPromptProvider(
      draftProviderId = providerPrefs.providerId ?: draft.providerId,
      preferredProvider = launcher?.preferredProvider(),
      availableProviders = providerSelector.availableProviders,
    )
    providerSelector.selectProvider(persistedProvider, providerPrefs.launchMode)
    if (effectiveProviderOptions.isEmpty()) {
      providerSelector.applyLegacyPlanModeSelection(providerSelector.selectedProvider?.bridge?.provider, draft.planModeEnabled)
    }
    updateProviderOptionsVisibility()

    setTargetMode(draft.targetMode)
    draftState.existingTaskSearchQuery = draft.existingTaskSearch
    existingTaskController.selectedExistingTaskId = draft.selectedExistingTaskId
    contextState.removedAutoLogicalItemIds.clear()
    if (contextRestoreSnapshot.contextFingerprint == contextState.initialAutoContextFingerprint) {
      val normalizedRemovedIds = normalizeRemovedContextItemIds(contextRestoreSnapshot.removedContextItemIds)
      contextState.removedAutoLogicalItemIds.addAll(normalizedRemovedIds)
      val restoredEntries = applyDraftContextRemovals(
        entries = contextState.autoContextEntries,
        currentFingerprint = contextState.initialAutoContextFingerprint,
        draftFingerprint = contextRestoreSnapshot.contextFingerprint,
        removedLogicalItemIds = normalizedRemovedIds,
      )
      if (restoredEntries != contextState.autoContextEntries) {
        contextState.autoContextEntries = restoredEntries
      }
    }
    contextState.manualContextItemsBySourceId.clear()
    contextState.manualContextItemsBySourceId.putAll(contextRestoreSnapshot.manualContextItemsBySourceId)
    refreshContextEntries()
    resolveExtensionTabs()

    if (draft.targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }

    return draft
  }

  fun restoreTaskDrafts(draft: AgentPromptUiDraft) {
    draftState.taskPromptStates.clear()
    val savedDrafts = draft.taskDrafts

    val newTaskKey = PromptTargetMode.NEW_TASK.name
    draftState.taskPromptStates[newTaskKey] = restoredTaskPromptDraftState(savedDrafts[newTaskKey] ?: draft.promptText)

    val existingTaskKey = PromptTargetMode.EXISTING_TASK.name
    savedDrafts[existingTaskKey]?.let { draftState.taskPromptStates[existingTaskKey] = restoredTaskPromptDraftState(it) }

    for (entry in contextState.activeExtensionTabs) {
      val savedText = savedDrafts[entry.taskKey]
      if (!savedText.isNullOrBlank()) {
        draftState.taskPromptStates[entry.taskKey] = restoredTaskPromptDraftState(savedText)
      }
      else {
        val initialText = entry.extension.getInitialPromptText(invocationData.project)
        if (!initialText.isNullOrBlank()) {
          draftState.taskPromptStates[entry.taskKey] = restoredTaskPromptDraftState(initialText)
        }
      }
    }
  }

  fun saveProviderPreferences() {
    launcherProvider()?.saveProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        launchMode = providerSelector.selectedLaunchMode,
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
      )
    )
  }

  fun saveDraft(currentTargetMode: PromptTargetMode) {
    savePromptTextForSelectedTab()

    val allTaskDrafts = LinkedHashMap<String, String>(draftState.taskPromptStates.size)
    draftState.taskPromptStates.forEach { (taskKey, state) ->
      allTaskDrafts[taskKey] = state.persistedUserText
    }

    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = allTaskDrafts[PromptTargetMode.NEW_TASK.name] ?: "",
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        targetMode = currentTargetMode,
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = draftState.existingTaskSearchQuery,
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        taskDrafts = allTaskDrafts,
        planModeEnabled = providerSelector.selectedProvider
                            ?.bridge
                            ?.provider
                            ?.let(providerSelector::selectedOptionIds)
                            ?.contains(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
                          ?: true,
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
      )
    )
    uiStateService.saveContextRestoreSnapshot(
      AgentPromptUiContextRestoreSnapshot(
        contextFingerprint = contextState.initialAutoContextFingerprint,
        removedContextItemIds = normalizeRemovedContextItemIds(contextState.removedAutoLogicalItemIds),
        manualContextItemsBySourceId = copyManualContextItemsBySourceId(),
      )
    )
  }

  fun savePromptTextForSelectedTab() {
    syncLivePromptTextForSelectedTab(promptArea.text)
  }

  fun loadPromptTextForSelectedTab() {
    val newPanel = tabbedPane.selectedComponent as? JPanel
    draftState.activeTaskKey = resolveTaskKey(newPanel)
    setPromptAreaText(draftState.activeTaskKey?.let { draftState.taskPromptStates[it]?.liveText } ?: "")
  }

  fun onPromptChanged() {
    if (!draftState.isProgrammaticPromptUpdate) {
      updateActiveTaskPromptState { state ->
        applyUserEditToDraftState(state, promptArea.text)
      }
    }
    else {
      syncLivePromptTextForSelectedTab(promptArea.text)
    }
  }

  fun applySuggestedPrompt(promptText: String) {
    updateActiveTaskPromptState { state ->
      applySuggestedPromptToDraftState(state, promptText)
    }
    setPromptAreaText(promptText)
  }

  fun removeTaskDraft(taskKey: String) {
    draftState.taskPromptStates.remove(taskKey)
  }

  private fun syncLivePromptTextForSelectedTab(promptText: String) {
    updateActiveTaskPromptState { state ->
      syncLivePromptTextForDraftState(state, promptText)
    }
  }

  private fun updateActiveTaskPromptState(update: (AgentPromptTaskDraftState) -> AgentPromptTaskDraftState) {
    val taskKey = draftState.activeTaskKey ?: return
    val currentState = draftState.taskPromptStates[taskKey] ?: restoredTaskPromptDraftState("")
    draftState.taskPromptStates[taskKey] = update(currentState)
  }

  private fun setPromptAreaText(promptText: String) {
    draftState.isProgrammaticPromptUpdate = true
    try {
      promptArea.text = promptText
    }
    finally {
      draftState.isProgrammaticPromptUpdate = false
    }
  }

  private fun copyManualContextItemsBySourceId(): LinkedHashMap<String, List<AgentPromptContextItem>> {
    val copy = LinkedHashMap<String, List<AgentPromptContextItem>>(contextState.manualContextItemsBySourceId.size)
    contextState.manualContextItemsBySourceId.forEach { (sourceId, items) ->
      copy[sourceId] = ArrayList(items)
    }
    return copy
  }
}
