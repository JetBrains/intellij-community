// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowViewType
import java.util.function.Supplier

class GHPRCreatePullRequestAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.create.show.form.action"),
                                                    Supplier { null },
                                                    AllIcons.General.Add) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    with(e) {
      val twController = project?.service<GHPRToolWindowController>()
      val twAvailable = project != null && twController != null && twController.isAvailable()
      val componentController = twController?.getTabController()?.componentController
      val twInitialized = project != null && componentController != null

      if (place == ActionPlaces.TOOLWINDOW_TITLE) {
        presentation.isEnabledAndVisible = twInitialized && componentController?.currentView != GHPRToolWindowViewType.NEW
        presentation.icon = AllIcons.General.Add
      }
      else {
        presentation.isEnabledAndVisible = twAvailable
        presentation.icon = AllIcons.Vcs.Vendors.Github
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val twController = e.getRequiredData(PlatformDataKeys.PROJECT).service<GHPRToolWindowController>()
    twController.activate {
      it.initialView = GHPRToolWindowViewType.NEW
      it.componentController?.createPullRequest()
    }
  }
}