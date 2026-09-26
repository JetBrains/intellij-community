// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MergeResolveActionProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MergeResolveActionProvider> =
      ExtensionPointName.create("com.intellij.openapi.vcs.merge.resolveActionProvider")
  }

  val action: AnAction

  val order: Int
    get() = 0
}
