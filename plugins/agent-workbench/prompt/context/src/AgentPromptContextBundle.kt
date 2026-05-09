// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val AGENT_PROMPT_CONTEXT_BUNDLE: @NonNls String = "messages.AgentPromptContextBundle"

internal object AgentPromptContextBundle {
  private val BUNDLE = DynamicBundle(AgentPromptContextBundle::class.java, AGENT_PROMPT_CONTEXT_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_PROMPT_CONTEXT_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}
