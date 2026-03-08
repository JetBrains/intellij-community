// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.jetbrains.annotations.NonNls

internal fun resolveDefaultFooterHintMessageKey(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProvider?,
): @NonNls String {
  return if (isTabQueueShortcutEnabled(targetMode = targetMode, selectedProvider = selectedProvider)) {
    "popup.footer.hint.existing.codex"
  }
  else {
    "popup.footer.hint"
  }
}

internal fun isTabQueueShortcutEnabled(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProvider?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK && selectedProvider == AgentSessionProvider.CODEX
}

internal fun shouldShowExistingTaskSelectionHint(
  targetMode: PromptTargetMode,
  selectedExistingTaskId: String?,
  selectedProvider: AgentSessionProvider?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK &&
         selectedExistingTaskId.isNullOrBlank() &&
         selectedProvider != AgentSessionProvider.CODEX
}

internal fun resolveEffectivePlanModeEnabled(
  supportsPlanMode: Boolean,
  isPlanModeSelected: Boolean,
  targetMode: PromptTargetMode,
  selectedThreadActivity: AgentThreadActivity?,
): Boolean {
  if (!supportsPlanMode || !isPlanModeSelected) {
    return false
  }
  if (targetMode != PromptTargetMode.EXISTING_TASK) {
    return true
  }
  return selectedThreadActivity != AgentThreadActivity.PROCESSING && selectedThreadActivity != AgentThreadActivity.REVIEWING
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
