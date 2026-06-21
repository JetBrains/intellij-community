// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

const val AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE: String = "plan_mode"

val AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION: AgentPromptProviderOption = AgentPromptProviderOption(
  id = AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE,
  labelKey = "toolwindow.prompt.option.plan.mode",
  labelFallback = "Plan mode",
  defaultSelected = true,
)

data class AgentPromptProviderOption(
  @JvmField val id: String,
  @JvmField val labelKey: String,
  @JvmField val labelFallback: String,
  @JvmField val defaultSelected: Boolean = false,
  @JvmField val enabledForNewTask: Boolean = true,
  @JvmField val enabledForExistingTask: Boolean = true,
)

enum class AgentPromptProviderOptionTarget {
  NEW_TASK,
  EXISTING_TASK,
}

fun resolveEffectiveProviderOptionIds(
  selectedProvider: AgentSessionProviderDescriptor?,
  selectedOptionIds: Set<String>,
  target: AgentPromptProviderOptionTarget,
): Set<String> {
  val bridge = selectedProvider ?: return emptySet()
  return bridge.promptOptions
    .asSequence()
    .filter { option -> option.id in selectedOptionIds }
    .filter { option ->
      when (target) {
        AgentPromptProviderOptionTarget.NEW_TASK -> option.enabledForNewTask
        AgentPromptProviderOptionTarget.EXISTING_TASK -> option.enabledForExistingTask
      }
    }
    .map(AgentPromptProviderOption::id)
    .toSet()
}
