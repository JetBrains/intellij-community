// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val CLAUDE_SESSIONS_BUNDLE: @NonNls String = "messages.ClaudeSessionsBundle"

internal object ClaudeSessionsBundle {
  private val BUNDLE = DynamicBundle(ClaudeSessionsBundle::class.java, CLAUDE_SESSIONS_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = CLAUDE_SESSIONS_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}

