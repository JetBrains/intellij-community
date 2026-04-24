// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val AGENT_PROMPT_SUGGESTIONS_BUNDLE: @NonNls String = "messages.AgentPromptSuggestionsBundle"

object AgentPromptSuggestionBundle {
  private val BUNDLE = DynamicBundle(AgentPromptSuggestionBundle::class.java, AGENT_PROMPT_SUGGESTIONS_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_PROMPT_SUGGESTIONS_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}
