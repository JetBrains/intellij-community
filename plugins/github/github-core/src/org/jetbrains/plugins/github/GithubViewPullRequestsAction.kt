// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel

class GithubViewPullRequestsAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return project.service<GHPRProjectViewModel>().isAvailable.value
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project!!.service<GHPRProjectViewModel>().activateAndAwaitProject {
      viewList()
    }
  }
}