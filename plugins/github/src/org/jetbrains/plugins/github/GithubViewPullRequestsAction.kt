// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowInitialView
import java.util.function.Supplier

class GithubViewPullRequestsAction :
  DumbAwareAction(GithubBundle.messagePointer("pull.request.view.list"),
                  Supplier { null },
                  AllIcons.Vcs.Vendors.Github) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return project.service<GHPRToolWindowController>().isAvailable()
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project!!.service<GHPRToolWindowController>().activate {
      it.initialView = GHPRToolWindowInitialView.LIST
      it.componentController?.viewList()
    }
  }
}