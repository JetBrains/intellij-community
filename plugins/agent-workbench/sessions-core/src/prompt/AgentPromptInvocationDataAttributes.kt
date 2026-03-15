// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import com.intellij.openapi.actionSystem.DataKey

const val AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY: String = "dataContext"
const val AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY: String = "preferExtensionTabs"

val AGENT_PROMPT_INITIAL_TEXT_DATA_KEY: DataKey<String> = DataKey.create("AgentPrompt.initialText")
