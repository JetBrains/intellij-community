// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitVcs

class GitMainMenuActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    val vcs = ProjectLevelVcsManager.getInstance(project).singleVCS ?: return
    if (vcs.name == GitVcs.NAME) {
      e.presentation.isEnabledAndVisible = true
    }
  }
}