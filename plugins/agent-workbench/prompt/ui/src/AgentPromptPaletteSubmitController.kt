// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec plugins/ij-air/spec/actions/global-prompt-entry.spec.md

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.dataContextOrNull
import com.intellij.agent.workbench.prompt.ui.context.buildExtensionActionDataContext
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPromptProviderOptionTarget
import com.intellij.platform.ai.agent.sessions.core.providers.isPlanModeBlockedForExistingThread
import com.intellij.platform.ai.agent.sessions.core.providers.resolveEffectiveProviderOptionIds
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorTextField
import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JList

internal class AgentPromptPaletteSubmitController(
  private val project: Project,
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: EditorTextField,
  private val providerSelector: AgentPromptProviderSelector,
  private val existingTaskController: AgentPromptExistingTaskController,
  private val sessionScope: CoroutineScope,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val launchState: AgentPromptPaletteLaunchState,
  private val currentTargetMode: () -> PromptTargetMode,
  private val activeExtensionTab: () -> AgentPromptPaletteExtensionTab?,
  private val buildVisibleContextEntries: () -> List<ContextEntry>,
  private val resolveContextSelection: (List<AgentPromptContextItem>, String?) -> AgentPromptPaletteContextSelection?,
  private val onWorkingProjectPathSelected: (String) -> Unit,
  private val onSubmitBlocked: (@Nls String) -> Unit,
  private val onSubmitSucceeded: () -> Unit,
  private val onPromptSubmitted: (AgentPromptHistoryEntry) -> Unit = {},
  private val launchProfileIdProvider: () -> String? = { null },
  private val launchTargetIdProvider: () -> String? = { null },
  private val generationSettingsProvider: () -> AgentPromptGenerationSettings = { AgentPromptGenerationSettings.AUTO },
  private val generationModelCatalogProvider: () -> List<AgentPromptGenerationModel> = { emptyList() },
  private val isContainerModeSelected: () -> Boolean = { false },
  private val isContainerModeSupported: (AgentSessionProvider) -> Boolean = { false },
  private val isContainerModeRuntimeAvailable: (AgentSessionProvider) -> Boolean = { false },
) {
  fun canSubmit(): Boolean = launchState.canSubmitNow

  fun resolveWorkingProjectPath(): String? {
    launchState.selectedWorkingProjectPath?.takeIf { path -> path.isNotBlank() }?.let { path ->
      return path
    }
    return launcherProvider()
      ?.resolveWorkingProjectPath(invocationData)
      ?.takeIf { path -> path.isNotBlank() }
  }

  fun resolveContextProjectBasePath(): String? {
    val workingProjectPath = resolveWorkingProjectPath()
    if (workingProjectPath != null) {
      return workingProjectPath
    }
    return if (launcherProvider() == null) {
      project.basePath?.takeIf { path -> path.isNotBlank() }
    }
    else {
      null
    }
  }

  fun updateSendAvailability() {
    val selectedProviderEntry = providerSelector.selectedProvider
    val hasPrompt = promptArea.document.immutableCharSequence.isNotBlank()
    val hasProjectPath = resolveWorkingProjectPath() != null
    val hasExistingTaskTarget = !existingTaskController.selectedExistingTaskId.isNullOrBlank()

    val extensionTab = activeExtensionTab()
    if (extensionTab?.extension?.getSubmitActionId() != null) {
      launchState.canSubmitNow = !launchState.launchInProgress
      return
    }
    val targetMode = if (extensionTab != null) PromptTargetMode.NEW_TASK else currentTargetMode()

    val submitPrerequisitesMet = hasPrompt &&
                                  hasProjectPath &&
                                  selectedProviderEntry != null &&
                                  selectedProviderEntry.isCliAvailable
    launchState.canSubmitNow = !launchState.launchInProgress && when (targetMode) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  fun submit() {
    if (launchState.launchInProgress) {
      return
    }

    fun reportValidationFailure(validationErrorKey: String, selectedProviderEntry: ProviderEntry?) {
      if (shouldRetrySubmitAfterWorkingProjectPathSelection(validationErrorKey) && launcherProvider()?.let(::promptWorkingProjectPathSelection) == true) {
        return
      }
      reportPromptSubmitBlocked(
        validationErrorKey = validationErrorKey,
        provider = selectedProviderEntry?.bridge?.provider,
        launchMode = providerSelector.selectedLaunchMode,
      )
      onSubmitBlocked(resolveValidationErrorMessage(validationErrorKey, selectedProviderEntry))
    }

    val extensionTab = activeExtensionTab()
    if (extensionTab != null) {
      val actionId = extensionTab.extension.getSubmitActionId()
      if (actionId != null) {
        val action = ActionManager.getInstance().getAction(actionId)
        if (action != null) {
          val baseDataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea)
          val contextProjectBasePath = resolveContextProjectBasePath()
          val contextSelection = resolveContextSelection(
            buildVisibleContextEntries().map(ContextEntry::item),
            contextProjectBasePath,
          ) ?: return
          val messageRequest = AgentPromptInitialMessageRequest(
            prompt = promptArea.text.trim(),
            projectPath = contextProjectBasePath,
            contextItems = contextSelection.items,
            contextEnvelopeSummary = contextSelection.summary,
          )
          val showsGenerationControls = extensionTab.extension.showsGenerationControls()
          val dataContext = buildExtensionActionDataContext(
            baseDataContext = baseDataContext,
            selectedProviderId = providerSelector.selectedProvider?.bridge?.provider?.value,
            selectedLaunchMode = providerSelector.selectedLaunchMode,
            messageRequest = messageRequest,
            generationSettings = if (showsGenerationControls) generationSettingsProvider() else null,
            generationModelCatalog = if (showsGenerationControls) generationModelCatalogProvider() else emptyList(),
          )
          val event = AnActionEvent.createEvent(action, dataContext, null, invocationData.actionPlace ?: "", ActionUiKind.NONE, null)
          action.actionPerformed(event)
          launchState.clearDraftOnClose = true
          onSubmitSucceeded()
          return
        }
      }
    }

    val extensionNormalLaunch = extensionTab != null
    val selectedProviderEntry = providerSelector.selectedProvider
    val launcher = launcherProvider()
    val projectPath = resolveWorkingProjectPath()
    val targetMode = if (extensionNormalLaunch) PromptTargetMode.NEW_TASK else currentTargetMode()
    val validationErrorKey = resolveSubmitValidationErrorMessageKey(
      targetMode = targetMode,
      prompt = promptArea.text,
      selectedProvider = selectedProviderEntry?.bridge?.provider,
      isProviderCliAvailable = selectedProviderEntry?.isCliAvailable == true,
      hasProjectPath = projectPath != null,
      hasLauncher = launcher != null,
      selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
    )
    if (validationErrorKey != null) {
      reportValidationFailure(validationErrorKey, selectedProviderEntry)
      return
    }

    val prompt = promptArea.text.trim()
    val providerEntry = selectedProviderEntry ?: return
    val effectiveProjectPath = projectPath ?: return
    val selectedThreadActivity = existingTaskController.selectedEntry()?.activity
    val effectiveProviderOptionIds = resolveEffectiveProviderOptionIds(
      selectedProvider = providerEntry.bridge,
      selectedOptionIds = providerSelector.selectedOptionIds(providerEntry.bridge.provider),
      target = when (targetMode) {
        PromptTargetMode.NEW_TASK -> AgentPromptProviderOptionTarget.NEW_TASK
        PromptTargetMode.EXISTING_TASK -> AgentPromptProviderOptionTarget.EXISTING_TASK
      },
    )
    val initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = prompt,
      projectPath = effectiveProjectPath,
      providerOptionIds = effectiveProviderOptionIds,
    )
    if (
      targetMode == PromptTargetMode.EXISTING_TASK &&
      providerEntry.bridge.isPlanModeBlockedForExistingThread(initialMessageRequest, selectedThreadActivity)
    ) {
      reportValidationFailure("popup.error.existing.plan.busy", selectedProviderEntry)
      return
    }

    val shouldStripContext = providerEntry.bridge.shouldStripContextForPrompt(prompt)
    val contextSelection = if (shouldStripContext) {
      null
    }
    else {
      val selectedContextItems = buildVisibleContextEntries().map(ContextEntry::item)
      resolveContextSelection(selectedContextItems, effectiveProjectPath) ?: return
    }
    val launcherBridge = launcher ?: return

    val targetThreadId = when {
      targetMode == PromptTargetMode.NEW_TASK -> null
      else -> existingTaskController.selectedExistingTaskId ?: return
    }
    val isNewTaskLaunch = targetThreadId == null
    val generationSettings = if (isNewTaskLaunch) generationSettingsProvider() else AgentPromptGenerationSettings.AUTO
    val generationModelCatalog = if (isNewTaskLaunch) generationModelCatalogProvider() else emptyList()

    val request = AgentPromptLaunchRequest(
      launchProfileId = launchProfileIdProvider(),
      provider = providerEntry.bridge.provider,
      projectPath = effectiveProjectPath,
      launchMode = providerSelector.selectedLaunchMode,
      launchTargetId = if (isNewTaskLaunch) launchTargetIdProvider() else null,
      initialMessageRequest = AgentPromptInitialMessageRequest(
        prompt = prompt,
        projectPath = effectiveProjectPath,
        contextItems = contextSelection?.items ?: emptyList(),
        contextEnvelopeSummary = contextSelection?.summary,
        providerOptionIds = effectiveProviderOptionIds,
      ),
      targetThreadId = targetThreadId,
      preferredDedicatedFrame = null,
      generationSettings = generationSettings,
      generationModelCatalog = generationModelCatalog,
      containerMode = shouldSubmitContainerMode(
        isSelected = isContainerModeSelected(),
        selectedProvider = providerEntry.bridge.provider,
        isExtensionTab = extensionNormalLaunch,
        supportsContainerMode = isContainerModeSupported,
        isContainerRuntimeAvailable = isContainerModeRuntimeAvailable,
      ),
    )

    launchState.launchInProgress = true
    updateSendAvailability()
    sessionScope.launch {
      val result = launcherBridge.launch(request)
      launchState.launchInProgress = false
      if (result.launched) {
        onPromptSubmitted(
          AgentPromptHistoryEntry(
            promptText = prompt,
            createdAtMs = System.currentTimeMillis(),
            providerId = providerEntry.bridge.provider.value,
            targetMode = targetMode,
            launchMode = providerSelector.selectedLaunchMode.name,
          )
        )
        launchState.clearDraftOnClose = true
        withContext(Dispatchers.UiWithModelAccess) {
          onSubmitSucceeded()
        }
        return@launch
      }

      updateSendAvailability()
      onSubmitBlocked(resolveLaunchErrorMessage(result.error))
    }
  }

  private fun resolveLaunchErrorMessage(error: AgentPromptLaunchError?): @Nls String {
    return when (error) {
      AgentPromptLaunchError.PROVIDER_UNAVAILABLE -> AgentPromptBundle.message("popup.error.launch.provider")
      AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentPromptBundle.message("popup.error.launch.mode")
      AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND -> AgentPromptBundle.message("popup.error.launch.thread.not.found")
      AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE -> AgentPromptBundle.message("popup.error.existing.plan.busy")
      AgentPromptLaunchError.CANCELLED,
      AgentPromptLaunchError.DROPPED_DUPLICATE,
      AgentPromptLaunchError.INTERNAL_ERROR,
      null,
        -> AgentPromptBundle.message("popup.error.launch.internal")
    }
  }

  private fun resolveValidationErrorMessage(
    validationErrorKey: String,
    selectedProviderEntry: ProviderEntry?,
  ): @Nls String {
    return if (validationErrorKey == "popup.error.provider.unavailable") {
      AgentPromptBundle.message(validationErrorKey, selectedProviderEntry?.displayName ?: "")
    }
    else {
      AgentPromptBundle.message(validationErrorKey)
    }
  }

  private fun promptWorkingProjectPathSelection(launcher: AgentPromptLauncherBridge): Boolean {
    val candidates = launcher.listWorkingProjectPathCandidates(invocationData)
      .asSequence()
      .filter { candidate -> candidate.path.isNotBlank() }
      .distinctBy(AgentPromptProjectPathCandidate::path)
      .toList()
    if (candidates.isEmpty()) {
      return false
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(candidates)
      .setTitle(AgentPromptBundle.message("popup.project.chooser.title"))
      .setRenderer(object : ColoredListCellRenderer<AgentPromptProjectPathCandidate>() {
        override fun customizeCellRenderer(
          list: JList<out AgentPromptProjectPathCandidate>,
          value: AgentPromptProjectPathCandidate?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          if (value == null) {
            return
          }
          append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          if (value.displayName != value.path) {
            append("  ${value.path}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }
      })
      .setItemChosenCallback { candidate ->
        launchState.selectedWorkingProjectPath = candidate.path
        onWorkingProjectPathSelected(candidate.path)
        submit()
      }
      .createPopup()
      .showInBestPositionFor(invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea))

    return true
  }
}
