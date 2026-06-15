// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AgentPromptLaunchProfileNameGeneratorTest {
  @Test
  fun highEffortGeneratesShortNameWithoutProvider() {
    val name = generatedLaunchProfileName(
      profile = profile(settings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH)),
      existingProfiles = emptyList(),
      models = emptyList(),
    )

    assertThat(name).isEqualTo("High")
  }

  @Test
  fun modelDisplayNameIsPreferredOverModelId() {
    val name = generatedLaunchProfileName(
      profile = profile(settings = AgentPromptGenerationSettings(modelId = "gpt-5")),
      existingProfiles = emptyList(),
      models = listOf(AgentPromptGenerationModel("gpt-5", "GPT-5")),
    )

    assertThat(name).isEqualTo("GPT-5")
  }

  @Test
  fun modelAndEffortAreCombined() {
    val name = generatedLaunchProfileName(
      profile = profile(settings = AgentPromptGenerationSettings(modelId = "gpt-5", reasoningEffort = AgentPromptReasoningEffort.HIGH)),
      existingProfiles = emptyList(),
      models = listOf(AgentPromptGenerationModel("gpt-5", "GPT-5")),
    )

    assertThat(name).isEqualTo("GPT-5 High")
  }

  @Test
  fun yoloModeUsesCompactModeLabel() {
    val name = generatedLaunchProfileName(
      profile = profile(launchMode = AgentSessionLaunchMode.YOLO),
      existingProfiles = emptyList(),
      models = emptyList(),
      compactLaunchModeLabel = "Full Auto",
    )

    assertThat(name).isEqualTo("Full Auto")
  }

  @Test
  fun planEffortIsIncludedWithoutProvider() {
    val name = generatedLaunchProfileName(
      profile = profile(settings = AgentPromptGenerationSettings(modelId = "gpt-5", planReasoningEffort = AgentPromptReasoningEffort.HIGH)),
      existingProfiles = emptyList(),
      models = listOf(AgentPromptGenerationModel("gpt-5", "GPT-5")),
    )

    assertThat(name).isEqualTo("GPT-5 Plan High")
  }

  @Test
  fun duplicateNamesGetNumericSuffix() {
    val name = generatedLaunchProfileName(
      profile = profile(settings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH)),
      existingProfiles = listOf(profile(name = "High"), profile(name = "High 2")),
      models = emptyList(),
    )

    assertThat(name).isEqualTo("High 3")
  }

  private fun profile(
    name: String = "",
    launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    settings: AgentPromptGenerationSettings = AgentPromptGenerationSettings(),
  ): AgentPromptLaunchProfile {
    return AgentPromptLaunchProfile(
      id = "",
      name = name,
      providerId = AgentSessionProvider.CODEX.value,
      launchMode = launchMode,
      generationSettings = settings,
    )
  }
}
