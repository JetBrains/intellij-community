// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.VcsShowToolWindowTabAction

class GitShowStashToolWindowTabAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = GitStashContentProvider.TAB_NAME

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null || !isStashToolWindowEnabled(project)) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}