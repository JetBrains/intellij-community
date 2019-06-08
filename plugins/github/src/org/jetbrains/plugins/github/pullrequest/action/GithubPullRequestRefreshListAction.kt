// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class GithubPullRequestRefreshListAction : DumbAwareAction("Refresh List", null, AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val context = e.getData(GithubPullRequestKeys.DATA_CONTEXT)
    e.presentation.isEnabled = context != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = e.getRequiredData(GithubPullRequestKeys.DATA_CONTEXT)
    context.repositoryDataLoader.reset()
    context.listLoader.reset()
    context.dataLoader.invalidateAllData()
  }
}