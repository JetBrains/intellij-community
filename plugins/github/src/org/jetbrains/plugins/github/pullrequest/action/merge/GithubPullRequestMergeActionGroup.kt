// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action.merge

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys

class GithubPullRequestMergeActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val permissions = e.getData(GithubPullRequestKeys.REPO_DETAILS)?.permissions
    e.presentation.isVisible = permissions != null && (permissions.isPush || permissions.isAdmin)
  }
}