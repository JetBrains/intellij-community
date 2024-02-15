// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import java.util.function.Supplier

class GHPRSelectPullRequestForFileAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.select.action"),
                                                           Supplier { null },
                                                           AllIcons.General.Locate) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val projectVm = project.serviceIfCreated<GHPRToolWindowViewModel>()?.projectVm?.value
    if (projectVm == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    val file: GHPRVirtualFile? = FileEditorManager.getInstance(project).selectedFiles.filterIsInstance<GHPRVirtualFile>().firstOrNull()
    e.presentation.isEnabled = file != null && file.repository == projectVm.repository
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(PlatformDataKeys.PROJECT) ?: return
    val file = FileEditorManager.getInstance(project).selectedFiles.filterIsInstance<GHPRVirtualFile>().first()
    project.service<GHPRToolWindowViewModel>().activateAndAwaitProject {
      if (file.repository == repository) {
        viewPullRequest(file.pullRequest)
      }
    }
  }
}