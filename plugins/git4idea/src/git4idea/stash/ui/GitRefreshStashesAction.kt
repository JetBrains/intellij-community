// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import git4idea.stash.GitStashTracker

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