// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.actions

import com.intellij.openapi.actionSystem.DataKey
import git4idea.GitReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GitDataKeys {
  @JvmField
  val SELECTED_REF: DataKey<GitReference> = DataKey.create("Git.Selected.Ref")
}