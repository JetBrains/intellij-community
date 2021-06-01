// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabController

class GHPRSwitchRemoteAction : DumbAwareAction(GithubBundle.message("pull.request.change.remote.or.account")) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val controller = e.project?.service<GHPRToolWindowController>()?.getTabController() ?: return false
    return controller.canResetRemoteOrAccount()
  }

  override fun actionPerformed(e: AnActionEvent) = e.project!!.service<GHPRToolWindowController>()
    .activate(GHPRToolWindowTabController::resetRemoteAndAccount)
}