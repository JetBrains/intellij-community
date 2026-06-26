// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensionContext
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
  private val getContainerModeSelected: () -> Boolean = { false },
  private val restoreContainerModeSelection: (Boolean) -> Unit = {},
) {
  fun snapshotPrompt(): AgentPromptPalettePromptSnapshot {
    return AgentPromptPalettePromptSnapshot(
      taskKey = draftState.activeTaskKey,
      taskState = draftState.activeTaskKey?.let { taskKey -> draftState.taskPromptStates[taskKey] },
      promptText = promptArea.text,
    )
  }

  fun previewPromptText(promptText: String) {
    setPromptAreaTextProgrammatically(promptText)
  }

  fun restorePromptSnapshot(snapshot: AgentPromptPalettePromptSnapshot) {
    val taskKey = snapshot.taskKey
    val taskState = snapshot.taskState
    if (taskKey != null && taskState != null) {
      draftState.taskPromptStates[taskKey] = taskState
    }
    setPromptAreaTextProgrammatically(snapshot.promptText)
  }

  fun replacePromptTextFromChooser(promptText: String) {
    updateActiveTaskPromptState { state ->
      applyUserEditToDraftState(state, promptText)
    }
    setPromptAreaTextProgrammatically(promptText)
  }

  fun restoreDraft(
    restoreContextSnapshot: Boolean = true,
    targetModeOverride: PromptTargetMode? = null,
  ): AgentPromptUiDraft {
    val draft = uiStateService.loadDraft()
    val targetMode = targetModeOverride ?: draft.targetMode
    val providerPrefs = launcherProvider()?.loadProviderPreferences() ?: AgentPromptLauncherBridge.ProviderPreferences()
    val contextRestoreSnapshot = uiStateService.loadContextRestoreSnapshot()
    setPromptAreaTextProgrammatically(draft.promptText)
    val effectiveProviderOptions = providerPrefs.providerOptionsByProviderId.ifEmpty { draft.providerOptionsByProviderId }
    providerSelector.restoreProviderOptionSelections(effectiveProviderOptions)
    updateProviderOptionsVisibility()

    restoreContainerModeSelection(providerPrefs.containerModeEnabled || draft.containerModeEnabled)
    setTargetMode(targetMode)
    draftState.existingTaskSearchQuery = draft.existingTaskSearch.takeIf { targetMode == PromptTargetMode.EXISTING_TASK }.orEmpty()
    existingTaskController.selectedExistingTaskId = draft.selectedExistingTaskId.takeIf { targetMode == PromptTargetMode.EXISTING_TASK }
    contextState.removedAutoLogicalItemIds.clear()
    if (restoreContextSnapshot) {
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
    }
    else {
      contextState.manualContextItemsBySourceId.clear()
    }
    refreshContextEntries()
    resolveExtensionTabs()

    if (targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }

    return if (targetMode == draft.targetMode) {
      draft
    }
    else {
      draft.copy(
        targetMode = targetMode,
        existingTaskSearch = "",
        selectedExistingTaskId = null,
      )
    }
  }

  fun restoreTaskDrafts(draft: AgentPromptUiDraft) {
    draftState.taskPromptStates.clear()
    val savedDrafts = draft.taskDrafts

    val newTaskKey = PromptTargetMode.NEW_TASK.name
    draftState.taskPromptStates[newTaskKey] = restoredTaskPromptDraftState(savedDrafts[newTaskKey] ?: draft.promptText)

    val existingTaskKey = PromptTargetMode.EXISTING_TASK.name
    savedDrafts[existingTaskKey]?.let { draftState.taskPromptStates[existingTaskKey] = restoredTaskPromptDraftState(it) }

    for ((extension, _, taskKeyPrefix) in contextState.activeExtensionTabs) {
      var foundSavedDraft = false
      savedDrafts.forEach { (taskKey, savedText) ->
        if (AgentPromptExtensionDraftDecisions.matchesTaskKey(taskKeyPrefix, taskKey)) {
          draftState.taskPromptStates[taskKey] = restoredTaskPromptDraftState(savedText)
          foundSavedDraft = true
        }
      }
      if (!foundSavedDraft) {
        val initialPrompt = extension.getInitialPrompt(invocationData.project)
        if (initialPrompt != null && initialPrompt.content.isNotBlank()) {
          val taskKey = AgentPromptExtensionDraftDecisions.taskKey(taskKeyPrefix, initialPrompt.kind)
          draftState.taskPromptStates[taskKey] = restoredTaskPromptDraftState(initialPrompt.content)
        }
      }
    }
  }

  fun overrideInitialTextIfProvided(initialText: String?) {
    if (!initialText.isNullOrBlank()) {
      val newTaskKey = PromptTargetMode.NEW_TASK.name
      draftState.taskPromptStates[newTaskKey] = restoredTaskPromptDraftState(initialText)
    }
  }

  fun saveProviderPreferences() {
    val currentPreferences = launcherProvider()?.loadProviderPreferences()
    launcherProvider()?.saveProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
        containerModeEnabled = getContainerModeSelected(),
        launchProfiles = currentPreferences?.launchProfiles.orEmpty(),
        defaultLaunchProfileId = currentPreferences?.defaultLaunchProfileId,
      )
    )
  }

  fun saveDraft(currentTargetMode: PromptTargetMode, selectedLaunchProfileId: String?) {
    savePromptTextForSelectedTab()

    val allTaskDrafts = LinkedHashMap<String, String>(draftState.taskPromptStates.size)
    draftState.taskPromptStates.forEach { (taskKey, state) ->
      if (contextState.activeExtensionTabs.any { entry ->
          AgentPromptExtensionDraftDecisions.matchesTaskKey(entry.taskKeyPrefix, taskKey)
        }) {
        return@forEach
      }
      allTaskDrafts[taskKey] = state.persistedUserText
    }
    val contextItems = contextState.contextEntries.map(ContextEntry::item)
    for ((extension, _, taskKeyPrefix) in contextState.activeExtensionTabs) {
      val extensionDrafts = AgentPromptPaletteExtensionContext.withContextItems(invocationData.project, contextItems) {
        AgentPromptExtensionDraftDecisions.persistTaskDrafts(
          taskKeyPrefix = taskKeyPrefix,
          taskStates = draftState.taskPromptStates,
          classifyPromptDraftKind = { promptText -> extension.classifyPromptDraftKind(invocationData.project, promptText) },
        )
      }
      allTaskDrafts.putAll(extensionDrafts)
    }
    val hasPersistedDraftText = allTaskDrafts.values.any { text -> normalizeAgentPromptText(text).isNotBlank() }

    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = allTaskDrafts[PromptTargetMode.NEW_TASK.name] ?: "",
        targetMode = currentTargetMode,
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = draftState.existingTaskSearchQuery,
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        taskDrafts = allTaskDrafts,
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
        containerModeEnabled = getContainerModeSelected(),
        selectedLaunchProfileId = selectedLaunchProfileId.takeIf { hasPersistedDraftText },
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
    setPromptAreaTextProgrammatically(draftState.activeTaskKey?.let { draftState.taskPromptStates[it]?.liveText } ?: "")
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
    setPromptAreaTextProgrammatically(promptText)
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

  fun setPromptAreaTextProgrammatically(promptText: String) {
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

internal data class AgentPromptPalettePromptSnapshot(
  @JvmField val taskKey: String?,
  @JvmField val taskState: AgentPromptTaskDraftState?,
  @JvmField val promptText: String,
)
