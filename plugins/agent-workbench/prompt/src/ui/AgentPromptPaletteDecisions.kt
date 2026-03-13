// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import org.jetbrains.annotations.NonNls

internal fun resolveDefaultFooterHintMessageKey(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProviderBridge?,
): @NonNls String {
  return if (isTabQueueShortcutEnabled(targetMode = targetMode, selectedProvider = selectedProvider)) {
    "popup.footer.hint.existing.queue"
  }
  else {
    "popup.footer.hint"
  }
}

internal fun isTabQueueShortcutEnabled(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProviderBridge?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK && selectedProvider?.supportsPromptTabQueueShortcut == true
}

internal fun shouldShowExistingTaskSelectionHint(
  targetMode: PromptTargetMode,
  selectedExistingTaskId: String?,
  selectedProvider: AgentSessionProviderBridge?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK &&
         selectedExistingTaskId.isNullOrBlank() &&
         selectedProvider?.suppressPromptExistingTaskSelectionHint != true
}

internal fun resolveEffectiveProviderOptionIds(
  selectedProvider: AgentSessionProviderBridge?,
  selectedOptionIds: Set<String>,
  targetMode: PromptTargetMode,
  selectedThreadActivity: AgentThreadActivity?,
): Set<String> {
  val bridge = selectedProvider ?: return emptySet()
  return bridge.promptOptions
    .asSequence()
    .filter { option -> option.id in selectedOptionIds }
    .filter { option ->
      when (targetMode) {
        PromptTargetMode.NEW_TASK -> option.enabledForNewTask
        PromptTargetMode.EXISTING_TASK -> {
          option.enabledForExistingTask && selectedThreadActivity !in option.disabledExistingTaskActivities
        }
      }
    }
    .map { option -> option.id }
    .toSet()
}

internal fun resolveEffectivePlanModeEnabled(
  selectedProvider: AgentSessionProviderBridge?,
  effectiveProviderOptionIds: Set<String>,
): Boolean {
  return selectedProvider?.supportsPlanMode == true && AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in effectiveProviderOptionIds
}

internal fun resolveSubmitValidationErrorMessageKey(
  targetMode: PromptTargetMode,
  prompt: String,
  selectedProvider: AgentSessionProvider?,
  isProviderCliAvailable: Boolean,
  hasProjectPath: Boolean,
  hasLauncher: Boolean,
  selectedExistingTaskId: String?,
): @NonNls String? {
  if (prompt.trim().isEmpty()) {
    return "popup.error.empty.prompt"
  }
  if (selectedProvider == null) {
    return "popup.error.no.providers"
  }
  if (!isProviderCliAvailable) {
    return "popup.error.provider.unavailable"
  }
  if (!hasProjectPath) {
    return "popup.error.project.path"
  }
  if (!hasLauncher) {
    return "popup.error.no.launcher"
  }
  if (targetMode == PromptTargetMode.EXISTING_TASK && selectedExistingTaskId.isNullOrBlank()) {
    return "popup.error.existing.select.task"
  }
  return null
}

internal fun shouldRetrySubmitAfterWorkingProjectPathSelection(
  validationErrorKey: String,
  requestWorkingProjectPathSelection: (() -> Boolean)? = null,
): Boolean {
  return validationErrorKey == "popup.error.project.path" && requestWorkingProjectPathSelection?.invoke() == true
}

internal fun reportPromptSubmitBlocked(
  validationErrorKey: String,
  provider: AgentSessionProvider?,
  launchMode: AgentSessionLaunchMode,
) {
  AgentWorkbenchTelemetry.logPromptSubmitBlocked(
    validationErrorKey = validationErrorKey,
    provider = provider,
    launchMode = launchMode,
  )
}

internal fun resolveRestoredPromptProvider(
  draftProviderId: String?,
  preferredProvider: AgentSessionProvider?,
  availableProviders: Iterable<AgentSessionProvider>,
): AgentSessionProvider? {
  val availableProviderSet = availableProviders.toSet()
  val draftProvider = draftProviderId
    ?.let(AgentSessionProvider::fromOrNull)
    ?.takeIf { provider -> provider in availableProviderSet }
  if (draftProvider != null) {
    return draftProvider
  }
  return preferredProvider?.takeIf { provider -> provider in availableProviderSet }
}
