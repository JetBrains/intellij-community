// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.VcsShowToolWindowTabAction
import git4idea.stash.isStashToolWindowAvailable

class GitShowStashToolWindowTabAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = GitStashContentProvider.TAB_NAME

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null || !isStashToolWindowAvailable(project)) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}