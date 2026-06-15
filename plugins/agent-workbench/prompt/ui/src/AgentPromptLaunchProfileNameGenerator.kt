// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

internal fun generatedLaunchProfileName(
  profile: AgentPromptLaunchProfile,
  existingProfiles: List<AgentPromptLaunchProfile>,
  models: List<AgentPromptGenerationModel>,
  compactLaunchModeLabel: @Nls String? = null,
): @NlsSafe String {
  val baseName = compactLaunchProfileBaseName(profile, models, compactLaunchModeLabel)
    .ifBlank { AgentPromptBundle.message("popup.profile.name.default") }
  return nextLaunchProfileName(baseName, existingProfiles.mapTo(HashSet()) { item -> item.name })
}

private fun compactLaunchProfileBaseName(
  profile: AgentPromptLaunchProfile,
  models: List<AgentPromptGenerationModel>,
  compactLaunchModeLabel: @Nls String?,
): @NlsSafe String {
  val settings = profile.generationSettings
  val modelName = settings.modelId?.let { modelId ->
    models.firstOrNull { model -> model.id == modelId }?.displayName ?: modelId
  }
  val parts = buildList {
    if (profile.launchMode == AgentSessionLaunchMode.YOLO) {
      add(compactLaunchModeLabel ?: AgentPromptBundle.message("popup.profile.generated.full.auto"))
    }
    modelName?.let(::add)
    if (settings.reasoningEffort != AgentPromptReasoningEffort.AUTO) {
      add(reasoningEffortPopupText(settings.reasoningEffort))
    }
    settings.planReasoningEffort?.takeIf { effort -> effort != AgentPromptReasoningEffort.AUTO }?.let { effort ->
      add(AgentPromptBundle.message("popup.profile.generated.plan.effort", reasoningEffortPopupText(effort)))
    }
  }
  return parts.joinToString(" ")
}

private fun nextLaunchProfileName(baseName: @NlsSafe String, existingNames: Set<String>): @NlsSafe String {
  if (baseName !in existingNames) return baseName
  var suffix = 2
  while ("$baseName $suffix" in existingNames) {
    suffix++
  }
  return "$baseName $suffix"
}
