// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object WorkspaceModelIdeBundle {
  @NonNls
  private const val BUNDLE_FQN = "messages.WorkspaceModelIdeBundle"
  private val BUNDLE = DynamicBundle(WorkspaceModelIdeBundle::class.java, BUNDLE_FQN)

  @Nls
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): String {
    return BUNDLE.getMessage(key, *params)
  }
}