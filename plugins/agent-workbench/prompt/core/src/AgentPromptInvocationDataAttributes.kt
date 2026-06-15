// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.actionSystem.DataKey

const val AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY: String = "dataContext"
const val AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY: String = "preferExtensionTabs"

val AGENT_PROMPT_INITIAL_TEXT_DATA_KEY: DataKey<String> = DataKey.create("AgentPrompt.initialText")
val AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY: DataKey<AgentPromptInitialMessageRequest> = DataKey.create("AgentPrompt.messageRequest")
val AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY: DataKey<String> = DataKey.create("AgentPrompt.selectedProviderId")

/** Launch generation settings (model, reasoning effort) chosen in the prompt palette for an extension submit action. */
val AGENT_PROMPT_GENERATION_SETTINGS_DATA_KEY: DataKey<AgentPromptGenerationSettings> = DataKey.create("AgentPrompt.generationSettings")

/** Provider model catalog backing [AGENT_PROMPT_GENERATION_SETTINGS_DATA_KEY], used to resolve the selected model on launch. */
val AGENT_PROMPT_GENERATION_MODEL_CATALOG_DATA_KEY: DataKey<List<AgentPromptGenerationModel>> = DataKey.create("AgentPrompt.generationModelCatalog")
