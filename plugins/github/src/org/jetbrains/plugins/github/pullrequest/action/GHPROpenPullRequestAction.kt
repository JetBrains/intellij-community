// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle

class GHPROpenPullRequestAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.open.action"),
                                                  GithubBundle.messagePointer("pull.request.open.action.description"),
                                                  null) {

  override fun update(e: AnActionEvent) {
    val controller = e.getData(GHPRActionKeys.PULL_REQUESTS_CONTROLLER)
    val selection = e.getData(GHPRActionKeys.SELECTED_PULL_REQUEST)
    val dataProvider = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    e.presentation.isEnabled = controller != null && (selection != null || dataProvider != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val controller = e.getRequiredData(GHPRActionKeys.PULL_REQUESTS_CONTROLLER)
    val selection = e.getData(GHPRActionKeys.SELECTED_PULL_REQUEST)
    val dataProvider = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    val pullRequest = selection ?: dataProvider!!.id

    controller.viewPullRequest(pullRequest)
    controller.openPullRequestTimeline(pullRequest, false)
  }
}