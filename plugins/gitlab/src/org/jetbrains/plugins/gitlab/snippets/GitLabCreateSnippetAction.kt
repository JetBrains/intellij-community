// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.api.data.GitLabVisibilityLevel
import org.jetbrains.plugins.gitlab.util.GitLabBundle.messagePointer

class GitLabCreateSnippetAction : DumbAwareAction(messagePointer("snippet.create.action.title"),
                                                  messagePointer("snippet.create.action.description"),
                                                  GitlabIcons.GitLabLogo) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    project.service<GitLabSnippetService>().performCreateSnippetAction(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {

  }
}