// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.withGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptGenerationModelSelectorTest {

  @Test
  fun `single group catalog on single group provider does not show separators`() {
    val models = listOf(
      AgentPromptGenerationModel("gpt-4o", "GPT-4o").withGroup(AgentPromptGenerationModelGroup.OPENAI),
      AgentPromptGenerationModel("gpt-3.5-turbo", "GPT-3.5 Turbo").withGroup(AgentPromptGenerationModelGroup.OPENAI)
    )
    val state = AgentPromptGenerationModelCatalogState.Loaded(models)
    val entries = buildGenerationModelSelectorEntries(providerId = "codex", catalogState = state, selectedModelId = null)

    assertThat(entries).hasSize(3)
    assertThat(entries[0]).isInstanceOf(AgentPromptGenerationModelSelectorEntry.Model::class.java)
    val autoModel = entries[0] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(autoModel.modelId).isNull()
    assertThat(autoModel.separatorGroup).isNull()

    val model1 = entries[1] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model1.modelId).isEqualTo("gpt-4o")
    assertThat(model1.separatorGroup).isNull()

    val model2 = entries[2] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model2.modelId).isEqualTo("gpt-3.5-turbo")
    assertThat(model2.separatorGroup).isNull()
  }

  @Test
  fun `single group catalog on multi group provider shows separators`() {
    val models = listOf(
      AgentPromptGenerationModel("gpt-4o", "GPT-4o").withGroup(AgentPromptGenerationModelGroup.OPENAI),
      AgentPromptGenerationModel("gpt-3.5-turbo", "GPT-3.5 Turbo").withGroup(AgentPromptGenerationModelGroup.OPENAI)
    )
    val state = AgentPromptGenerationModelCatalogState.Loaded(models)

    for (providerId in listOf("pi", "junie")) {
      val entries = buildGenerationModelSelectorEntries(providerId = providerId, catalogState = state, selectedModelId = null)

      assertThat(entries).hasSize(3)
      val autoModel = entries[0] as AgentPromptGenerationModelSelectorEntry.Model
      assertThat(autoModel.modelId).isNull()
      assertThat(autoModel.separatorGroup).isNull()

      val model1 = entries[1] as AgentPromptGenerationModelSelectorEntry.Model
      assertThat(model1.modelId).isEqualTo("gpt-4o")
      assertThat(model1.separatorGroup).isEqualTo(AgentPromptGenerationModelGroup.OPENAI)

      val model2 = entries[2] as AgentPromptGenerationModelSelectorEntry.Model
      assertThat(model2.modelId).isEqualTo("gpt-3.5-turbo")
      assertThat(model2.separatorGroup).isNull()
    }
  }

  @Test
  fun `multi group catalog shows separators for any provider`() {
    val models = listOf(
      AgentPromptGenerationModel("gpt-4o", "GPT-4o").withGroup(AgentPromptGenerationModelGroup.OPENAI),
      AgentPromptGenerationModel("claude-3-opus", "Claude 3 Opus").withGroup(AgentPromptGenerationModelGroup.CLAUDE_CODE)
    )
    val state = AgentPromptGenerationModelCatalogState.Loaded(models)
    val entries = buildGenerationModelSelectorEntries(providerId = "codex", catalogState = state, selectedModelId = null)

    assertThat(entries).hasSize(3)
    val autoModel = entries[0] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(autoModel.modelId).isNull()
    assertThat(autoModel.separatorGroup).isNull()

    val model1 = entries[1] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model1.modelId).isEqualTo("gpt-4o")
    assertThat(model1.separatorGroup).isEqualTo(AgentPromptGenerationModelGroup.OPENAI)

    val model2 = entries[2] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model2.modelId).isEqualTo("claude-3-opus")
    assertThat(model2.separatorGroup).isEqualTo(AgentPromptGenerationModelGroup.CLAUDE_CODE)
  }

  @Test
  fun `saved unknown model does not trigger group headers if only a single group in catalog`() {
    val models = listOf(
      AgentPromptGenerationModel("gpt-4o", "GPT-4o").withGroup(AgentPromptGenerationModelGroup.OPENAI)
    )
    val state = AgentPromptGenerationModelCatalogState.Loaded(models)
    val entries = buildGenerationModelSelectorEntries(providerId = "codex", catalogState = state, selectedModelId = "unknown-model")

    assertThat(entries).hasSize(3)
    val autoModel = entries[0] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(autoModel.modelId).isNull()
    assertThat(autoModel.separatorGroup).isNull()

    val model1 = entries[1] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model1.modelId).isEqualTo("gpt-4o")
    assertThat(model1.separatorGroup).isNull()

    val model2 = entries[2] as AgentPromptGenerationModelSelectorEntry.Model
    assertThat(model2.modelId).isEqualTo("unknown-model")
    assertThat(model2.separatorGroup).isNull()
  }
}
