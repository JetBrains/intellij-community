// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.ApiStatus

const val AGENT_THREAD_VIEW_BUNDLE: @NonNls String = "messages.AgentThreadViewBundle"

@ApiStatus.Internal
object AgentThreadViewBundle {
  private val BUNDLE = DynamicBundle(AgentThreadViewBundle::class.java, AGENT_THREAD_VIEW_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = AGENT_THREAD_VIEW_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}
