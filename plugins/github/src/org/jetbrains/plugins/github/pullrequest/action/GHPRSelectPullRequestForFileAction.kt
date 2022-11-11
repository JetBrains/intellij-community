// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile
import java.util.function.Supplier

class GHPRSelectPullRequestForFileAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.select.action"),
                                                           Supplier<String?> { null },
                                                           AllIcons.General.Locate) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val componentController = project.service<GHPRToolWindowController>().getTabController()?.componentController
    if (componentController == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    val files = FileEditorManager.getInstance(project).selectedFiles.filterIsInstance<GHPRVirtualFile>()
    e.presentation.isEnabled = files.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(PlatformDataKeys.PROJECT)
    val file = FileEditorManager.getInstance(project).selectedFiles.filterIsInstance<GHPRVirtualFile>().first()
    project.service<GHPRToolWindowController>().activate {
      it.componentController?.viewPullRequest(file.pullRequest)
    }
  }
}