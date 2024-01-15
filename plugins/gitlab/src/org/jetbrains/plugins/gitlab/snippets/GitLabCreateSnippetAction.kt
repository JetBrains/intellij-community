// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.SnippetAction.CREATE_OPEN_DIALOG
import org.jetbrains.plugins.gitlab.util.GitLabStatistics.logSnippetActionExecuted

class GitLabCreateSnippetAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)

    val editor = e.getData(CommonDataKeys.EDITOR)
    val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
    
    logSnippetActionExecuted(project, CREATE_OPEN_DIALOG)

    project.service<GitLabSnippetService>().performCreateSnippetAction(editor, selectedFile, selectedFiles)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val editor = e.getData(CommonDataKeys.EDITOR)
    val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()

    e.presentation.isEnabledAndVisible =
      project.service<GitLabProjectsManager>().knownRepositoriesState.value.isNotEmpty() &&
      project.service<GitLabSnippetService>().canCreateSnippet(editor, selectedFile, selectedFiles)
  }
}