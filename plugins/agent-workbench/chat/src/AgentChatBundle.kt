// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val AGENT_CHAT_BUNDLE: @NonNls String = "messages.AgentChatBundle"

internal object AgentChatBundle {
  private val BUNDLE = DynamicBundle(AgentChatBundle::class.java, AGENT_CHAT_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_CHAT_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}
