// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import org.jetbrains.annotations.Nls

internal data class AgentPromptGenerationModelGroupSection(
  @JvmField val group: AgentPromptGenerationModelGroup,
  @JvmField val models: List<AgentPromptGenerationModel>,
)

internal fun List<AgentPromptGenerationModel>.groupedForModelSelector(): List<AgentPromptGenerationModelGroupSection> {
  val modelsByGroup = LinkedHashMap<AgentPromptGenerationModelGroup, MutableList<AgentPromptGenerationModel>>()
  for (model in this) {
    modelsByGroup.getOrPut(model.group) { ArrayList() }.add(model)
  }
  return AGENT_PROMPT_GENERATION_MODEL_GROUP_ORDER.mapNotNull { group ->
    modelsByGroup[group]?.let { models -> AgentPromptGenerationModelGroupSection(group, models) }
  }
}

internal fun AgentPromptGenerationModelGroup.modelSelectorText(): @Nls String {
  return when (this) {
    AgentPromptGenerationModelGroup.LOCAL -> AgentPromptBundle.message("popup.generation.model.group.local")
    AgentPromptGenerationModelGroup.OPENAI -> AgentPromptBundle.message("popup.generation.model.group.openai")
    AgentPromptGenerationModelGroup.CLAUDE_CODE -> AgentPromptBundle.message("popup.generation.model.group.claude.code")
    AgentPromptGenerationModelGroup.OTHER -> AgentPromptBundle.message("popup.generation.model.group.other")
  }
}

private val AGENT_PROMPT_GENERATION_MODEL_GROUP_ORDER: List<AgentPromptGenerationModelGroup> = listOf(
  AgentPromptGenerationModelGroup.LOCAL,
  AgentPromptGenerationModelGroup.OPENAI,
  AgentPromptGenerationModelGroup.CLAUDE_CODE,
  AgentPromptGenerationModelGroup.OTHER,
)
