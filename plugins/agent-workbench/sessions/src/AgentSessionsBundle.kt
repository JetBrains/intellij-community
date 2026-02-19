// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

const val AGENT_SESSIONS_BUNDLE: @NonNls String = "messages.AgentSessionsBundle"

internal object AgentSessionsBundle {
  private val BUNDLE = DynamicBundle(AgentSessionsBundle::class.java, AGENT_SESSIONS_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_SESSIONS_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }

  fun messagePointer(
    key: @PropertyKey(resourceBundle = AGENT_SESSIONS_BUNDLE) String,
    vararg params: Any,
  ): Supplier<@Nls String> {
    return BUNDLE.getLazyMessage(key, *params)
  }
}
