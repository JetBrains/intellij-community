// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOptionTarget
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.resolveEffectiveProviderOptionIds as resolveCoreEffectiveProviderOptionIds
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent

internal fun resolveDefaultFooterHintMessageKey(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProviderDescriptor?,
  hasNextPromptTab: Boolean = false,
): @NonNls String {
  return if (isTabQueueShortcutEnabled(
      targetMode = targetMode,
      selectedProvider = selectedProvider,
      hasNextPromptTab = hasNextPromptTab,
    )) {
    "popup.footer.hint.existing.queue"
  }
  else {
    "popup.footer.hint"
  }
}

internal fun isTabQueueShortcutEnabled(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProviderDescriptor?,
  hasNextPromptTab: Boolean = false,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK &&
         selectedProvider?.supportsPromptTabQueueShortcut == true &&
         !hasNextPromptTab
}

internal fun shouldShowExistingTaskSelectionHint(
  targetMode: PromptTargetMode,
  selectedExistingTaskId: String?,
  selectedProvider: AgentSessionProviderDescriptor?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK &&
         selectedExistingTaskId.isNullOrBlank() &&
         selectedProvider?.suppressPromptExistingTaskSelectionHint != true
}

internal fun shouldShowContainerModeOption(
  selectedProvider: AgentSessionProvider?,
  isExtensionTab: Boolean,
  supportsContainerMode: (AgentSessionProvider) -> Boolean,
): Boolean {
  return !isExtensionTab && selectedProvider?.let(supportsContainerMode) == true
}

internal fun shouldEnableContainerModeOption(
  selectedProvider: AgentSessionProvider?,
  isExtensionTab: Boolean,
  supportsContainerMode: (AgentSessionProvider) -> Boolean,
  isContainerRuntimeAvailable: (AgentSessionProvider) -> Boolean,
): Boolean {
  return shouldShowContainerModeOption(
    selectedProvider = selectedProvider,
    isExtensionTab = isExtensionTab,
    supportsContainerMode = supportsContainerMode,
  ) && selectedProvider?.let(isContainerRuntimeAvailable) == true
}

internal fun resolveContainerModeOptionState(
  selectedProvider: AgentSessionProvider?,
  isExtensionTab: Boolean,
  requestedSelection: Boolean,
  supportsContainerMode: (AgentSessionProvider) -> Boolean,
  isContainerRuntimeAvailable: (AgentSessionProvider) -> Boolean,
): ContainerModeOptionState {
  val visible = shouldShowContainerModeOption(
    selectedProvider = selectedProvider,
    isExtensionTab = isExtensionTab,
    supportsContainerMode = supportsContainerMode,
  )
  val enabled = visible && selectedProvider?.let(isContainerRuntimeAvailable) == true
  return ContainerModeOptionState(
    visible = visible,
    enabled = enabled,
    selected = requestedSelection && enabled,
    showUnavailableTooltip = visible && !enabled,
  )
}

internal data class ContainerModeOptionState(
  @JvmField val visible: Boolean,
  @JvmField val enabled: Boolean,
  @JvmField val selected: Boolean,
  @JvmField val showUnavailableTooltip: Boolean,
)

internal fun shouldSubmitContainerMode(
  isSelected: Boolean,
  selectedProvider: AgentSessionProvider?,
  isExtensionTab: Boolean,
  supportsContainerMode: (AgentSessionProvider) -> Boolean,
  isContainerRuntimeAvailable: (AgentSessionProvider) -> Boolean,
): Boolean {
  return isSelected && shouldEnableContainerModeOption(
    selectedProvider = selectedProvider,
    isExtensionTab = isExtensionTab,
    supportsContainerMode = supportsContainerMode,
    isContainerRuntimeAvailable = isContainerRuntimeAvailable,
  )
}

internal fun resolveEffectiveProviderOptionIds(
  selectedProvider: AgentSessionProviderDescriptor?,
  selectedOptionIds: Set<String>,
  targetMode: PromptTargetMode,
): Set<String> {
  return resolveCoreEffectiveProviderOptionIds(
    selectedProvider = selectedProvider,
    selectedOptionIds = selectedOptionIds,
    target = when (targetMode) {
      PromptTargetMode.NEW_TASK -> AgentPromptProviderOptionTarget.NEW_TASK
      PromptTargetMode.EXISTING_TASK -> AgentPromptProviderOptionTarget.EXISTING_TASK
    },
  )
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

internal fun shouldRefocusPromptOnFrameActivated(
  popupProject: Project,
  activatedProject: Project?,
  isPopupVisible: Boolean,
): Boolean {
  return isPopupVisible && activatedProject === popupProject
}

// Dismiss on outside clicks inside the originating IDE frame, unless the click is the one that
// just activated the frame from an inactive state. Cross-frame clicks and keyboard/app focus
// transfers never dismiss. Escape and programmatic submits always close.
internal fun shouldAllowPromptPopupCancellation(
  popupProject: Project?,
  isRecentSourceFrameActivation: Boolean,
  currentEvent: AWTEvent?,
  isExplicitClose: Boolean,
  resolveProject: (Component?) -> Project?,
  autoClose: Boolean = true
): Boolean {
  return isExplicitClose || popupProject == null || when (currentEvent) {
    is MouseEvent -> {
      if (!autoClose) return false

      !isRecentSourceFrameActivation &&
      resolveProject(currentEvent.component) === popupProject
    }
    is KeyEvent -> isEscapeKeyPress(currentEvent)
    is WindowEvent -> false
    else -> true
  }
}

private fun isEscapeKeyPress(event: KeyEvent): Boolean {
  return event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE && event.modifiersEx == 0
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
