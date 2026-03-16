// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.terminal

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

internal object GitTerminalBundle {
  @NonNls
  const val BUNDLE: String = "messages.GitTerminalBundle"
  private val INSTANCE = DynamicBundle(GitTerminalBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return INSTANCE.getMessage(key, *params)
  }
}