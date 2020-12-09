// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import git4idea.stash.GitStashTracker
import git4idea.stash.isStashToolWindowEnabled

class GitRefreshStashesAction: DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !isStashToolWindowEnabled(project)) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project!!.service<GitStashTracker>().scheduleRefresh()
  }
}