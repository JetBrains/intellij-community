// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

internal object GitFrontendBundle {
  const val BUNDLE: @NonNls String = "messages.GitFrontendBundle"
  val INSTANCE: DynamicBundle = DynamicBundle(GitFrontendBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String = INSTANCE.getMessage(key, *params)
}