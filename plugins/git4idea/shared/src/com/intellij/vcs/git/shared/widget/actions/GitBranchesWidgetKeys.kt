// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.actions

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.vcs.git.shared.widget.popup.GitBranchesWidgetPopup
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GitBranchesWidgetKeys {
  val POPUP: DataKey<GitBranchesWidgetPopup> = DataKey.create("GIT_BRANCHES_TREE_POPUP")
}