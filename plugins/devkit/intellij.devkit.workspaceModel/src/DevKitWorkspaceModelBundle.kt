// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

// lul

@NonNls
private const val BUNDLE_FQN = "messages.DevKitWorkspaceModelBundle"

object DevKitWorkspaceModelBundle {
  private val BUNDLE = DynamicBundle(DevKitWorkspaceModelBundle::class.java, BUNDLE_FQN)
  
  @Nls
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): String {
    return BUNDLE.getMessage(key, *params)
  }
}
