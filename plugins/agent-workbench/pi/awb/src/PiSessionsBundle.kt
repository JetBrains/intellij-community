// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val PI_SESSIONS_BUNDLE: @NonNls String = "messages.PiSessionsBundle"

internal object PiSessionsBundle {
  private val BUNDLE = DynamicBundle(PiSessionsBundle::class.java, PI_SESSIONS_BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = PI_SESSIONS_BUNDLE) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}

