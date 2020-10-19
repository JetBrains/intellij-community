// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import java.util.function.Supplier

class GHPRCreatePullRequestAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.create.action"),
                                                    Supplier { null },
                                                    AllIcons.General.Add) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null &&
                                         e.project?.service<GHPRToolWindowController>()?.getTabController()?.componentController != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(PlatformDataKeys.PROJECT).service<GHPRToolWindowController>()
      .getTabController()?.componentController?.createPullRequest()
  }
}