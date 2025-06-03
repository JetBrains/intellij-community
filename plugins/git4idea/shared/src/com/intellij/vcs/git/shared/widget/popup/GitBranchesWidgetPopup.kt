// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.popup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.vcs.git.shared.repo.GitRepositoryFrontendModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface GitBranchesWidgetPopup: JBPopup {
  val userResized: Boolean

  var groupByPrefix: Boolean

  fun restoreDefaultSize()

  @TestOnly
  fun promiseExpandTree(): Promise<*>

  @TestOnly
  fun getExpandedPathsSize(): Int

  companion object {
    fun createPopup(project: Project, preferredSelection: GitRepositoryFrontendModel?): GitBranchesWidgetPopup =
      GitBranchesTreePopup.create(project, preferredSelection)
  }
}