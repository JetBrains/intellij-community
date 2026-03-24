// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.agent.workbench.prompt.ui.context.buildExtensionActionDataContext
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorTextField
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import javax.swing.JList

internal class AgentPromptPaletteSubmitController(
  private val project: Project,
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: EditorTextField,
  private val providerSelector: AgentPromptProviderSelector,
  private val existingTaskController: AgentPromptExistingTaskController,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val launchState: AgentPromptPaletteLaunchState,
  private val currentTargetMode: () -> PromptTargetMode,
  private val activeExtensionTab: () -> AgentPromptPaletteExtensionTab?,
  private val buildVisibleContextEntries: () -> List<ContextEntry>,
  private val resolveContextSelection: (List<AgentPromptContextItem>, String?) -> AgentPromptPaletteContextSelection?,
  private val onWorkingProjectPathSelected: (String) -> Unit,
  private val onSubmitBlocked: (@Nls String) -> Unit,
  private val onSubmitSucceeded: () -> Unit,
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

    if (activeExtensionTab() != null) {
      launchState.canSubmitNow = true
      return
    }

    val submitPrerequisitesMet = hasPrompt &&
                                 hasProjectPath &&
                                 selectedProviderEntry != null &&
                                 selectedProviderEntry.isCliAvailable
    launchState.canSubmitNow = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  fun submit() {
    val extensionTab = activeExtensionTab()
    if (extensionTab != null) {
      val actionId = extensionTab.extension.getSubmitActionId()
      if (actionId != null) {
        val action = ActionManager.getInstance().getAction(actionId)
        if (action != null) {
          val baseDataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea)
          val dataContext = buildExtensionActionDataContext(
            baseDataContext = baseDataContext,
            selectedProviderId = providerSelector.selectedProvider?.bridge?.provider?.value,
          )
          val event = AnActionEvent.createEvent(action, dataContext, null, invocationData.actionPlace ?: "", ActionUiKind.NONE, null)
          action.actionPerformed(event)
          launchState.clearDraftOnClose = true
          onSubmitSucceeded()
          return
        }
      }
    }

    val selectedProviderEntry = providerSelector.selectedProvider
    val launcher = launcherProvider()
    val projectPath = resolveWorkingProjectPath()
    val validationErrorKey = resolveSubmitValidationErrorMessageKey(
      targetMode = currentTargetMode(),
      prompt = promptArea.text,
      selectedProvider = selectedProviderEntry?.bridge?.provider,
      isProviderCliAvailable = selectedProviderEntry?.isCliAvailable == true,
      hasProjectPath = projectPath != null,
      hasLauncher = launcher != null,
      selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
    )
    if (validationErrorKey != null) {
      if (shouldRetrySubmitAfterWorkingProjectPathSelection(validationErrorKey) && launcher != null && promptWorkingProjectPathSelection(launcher)) {
        return
      }
      reportPromptSubmitBlocked(
        validationErrorKey = validationErrorKey,
        provider = selectedProviderEntry?.bridge?.provider,
        launchMode = providerSelector.selectedLaunchMode,
      )
      onSubmitBlocked(resolveValidationErrorMessage(validationErrorKey, selectedProviderEntry))
      return
    }

    val prompt = promptArea.text.trim()
    val providerEntry = selectedProviderEntry ?: return
    val effectiveProjectPath = projectPath ?: return

    val selectedContextItems = buildVisibleContextEntries().map(ContextEntry::item)
    val contextSelection = resolveContextSelection(selectedContextItems, effectiveProjectPath) ?: return
    val launcherBridge = launcher ?: return

    val targetThreadId = when {
      activeExtensionTab() != null -> null
      currentTargetMode() == PromptTargetMode.NEW_TASK -> null
      else -> existingTaskController.selectedExistingTaskId ?: return
    }
    val effectiveProviderOptionIds = resolveEffectiveProviderOptionIds(
      selectedProvider = providerEntry.bridge,
      selectedOptionIds = providerSelector.selectedOptionIds(providerEntry.bridge.provider),
      targetMode = currentTargetMode(),
      selectedThreadActivity = existingTaskController.selectedEntry()?.activity,
    )
    val effectivePlanModeEnabled = if (activeExtensionTab() != null) {
      false
    }
    else {
      resolveEffectivePlanModeEnabled(
        selectedProvider = providerEntry.bridge,
        effectiveProviderOptionIds = effectiveProviderOptionIds,
      )
    }

    val request = AgentPromptLaunchRequest(
      provider = providerEntry.bridge.provider,
      projectPath = effectiveProjectPath,
      launchMode = providerSelector.selectedLaunchMode,
      initialMessageRequest = AgentPromptInitialMessageRequest(
        prompt = prompt,
        projectPath = effectiveProjectPath,
        contextItems = contextSelection.items,
        contextEnvelopeSummary = contextSelection.summary,
        planModeEnabled = effectivePlanModeEnabled,
        providerOptionIds = effectiveProviderOptionIds,
      ),
      targetThreadId = targetThreadId,
      preferredDedicatedFrame = null,
    )

    val result = launcherBridge.launch(request)
    if (result.launched) {
      launchState.clearDraftOnClose = true
      onSubmitSucceeded()
      return
    }

    val errorMessage = when (result.error) {
      AgentPromptLaunchError.PROVIDER_UNAVAILABLE -> AgentPromptBundle.message("popup.error.launch.provider")
      AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentPromptBundle.message("popup.error.launch.mode")
      AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND -> AgentPromptBundle.message("popup.error.launch.thread.not.found")
      AgentPromptLaunchError.CANCELLED,
      AgentPromptLaunchError.DROPPED_DUPLICATE,
      AgentPromptLaunchError.INTERNAL_ERROR,
      null,
        -> AgentPromptBundle.message("popup.error.launch.internal")
    }
    onSubmitBlocked(errorMessage)
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
