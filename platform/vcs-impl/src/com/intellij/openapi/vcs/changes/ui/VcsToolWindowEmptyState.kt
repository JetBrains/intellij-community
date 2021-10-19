// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CONTEXT_HELP
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.HELP_ID
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.util.ui.StatusText
import java.awt.event.InputEvent

private const val ACTION_LOCAL_HISTORY = "LocalHistory.ShowHistory"

internal fun StatusText.setChangesViewEmptyState(project: Project) {
  appendLine(message("status.text.vcs.toolwindow"))
  findCreateRepositoryAction()?.let { action ->
    appendLine(message("status.text.vcs.toolwindow.create.repository"), LINK_PLAIN_ATTRIBUTES) {
      invokeAction(project, it.source, action)
    }
  }
  appendLine(message("status.text.vcs.toolwindow.local.history"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(project, it.source, ACTION_LOCAL_HISTORY)
  }
  appendLine("")
  appendLine(AllIcons.General.ContextHelp, message("status.text.vcs.toolwindow.help"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(project, it.source, ACTION_CONTEXT_HELP)
  }
}

private fun findCreateRepositoryAction(): AnAction? {
  val group = ActionManager.getInstance().getAction("Vcs.ToolWindow.CreateRepository") as? ActionGroup
  return group?.getChildren(null)?.firstOrNull()
}

private fun invokeAction(project: Project, source: Any?, actionId: String) {
  val action = ActionManager.getInstance().getAction(actionId) ?: return
  invokeAction(project, source, action)
}

private fun invokeAction(project: Project, source: Any?, action: AnAction) =
  invokeAction(action, createDataContext(project), ActionPlaces.UNKNOWN, source as? InputEvent, null)

private fun createDataContext(project: Project): DataContext =
  SimpleDataContext.builder()
    .add(PROJECT, project)
    .add(VIRTUAL_FILE, project.guessProjectDir())
    .add(HELP_ID, "version.control.empty.state")
    .build()