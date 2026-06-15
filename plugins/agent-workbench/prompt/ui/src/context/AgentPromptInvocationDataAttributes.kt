// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_GENERATION_MODEL_CATALOG_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_GENERATION_SETTINGS_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY as CORE_INVOCATION_DATA_CONTEXT_KEY

internal const val AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY: String = CORE_INVOCATION_DATA_CONTEXT_KEY
internal const val AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY: String = "actionEvent"

internal fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
    return attributes[AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}

internal fun buildExtensionActionDataContext(
    baseDataContext: DataContext,
    selectedProviderId: String?,
    messageRequest: AgentPromptInitialMessageRequest? = null,
    generationSettings: AgentPromptGenerationSettings? = null,
    generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
): DataContext {
    if (selectedProviderId.isNullOrBlank() && messageRequest == null && generationSettings == null) {
        return baseDataContext
    }

    return CustomizedDataContext.withSnapshot(baseDataContext) { sink: DataSink ->
        if (!selectedProviderId.isNullOrBlank()) {
            sink[AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY] = selectedProviderId
        }
        if (messageRequest != null) {
            sink[AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY] = messageRequest
        }
        if (generationSettings != null) {
            sink[AGENT_PROMPT_GENERATION_SETTINGS_DATA_KEY] = generationSettings
            sink[AGENT_PROMPT_GENERATION_MODEL_CATALOG_DATA_KEY] = generationModelCatalog
        }
    }
}
