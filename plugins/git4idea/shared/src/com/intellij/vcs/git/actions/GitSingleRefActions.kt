// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import git4idea.GitReference
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object GitSingleRefActions {
  @Language("devkit-action-id")
  private const val GIT_SINGLE_REF_ACTION_GROUP: @NonNls String = "Git.Branch"

  @JvmField
  val SELECTED_REF_DATA_KEY: DataKey<GitReference> = DataKey.create("Git.Selected.Ref")

  @JvmStatic
  fun getSingleRefActionGroup(): ActionGroup =
    ActionManager.getInstance().getAction(GIT_SINGLE_REF_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
}