// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction

abstract class GHPullRequestURLAction : DumbAwareAction() {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun update(e: AnActionEvent) {
    val url = findPullRequestURL(e.dataContext)
    e.presentation.isEnabledAndVisible = url != null
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val url = findPullRequestURL(e.dataContext) ?: return
    handleURL(url)
  }

  abstract fun handleURL(pullRequestUrl: String)

  private fun findPullRequestURL(ctx: DataContext): String? = ctx.getData(GHPRActionKeys.PULL_REQUEST_URL)
}