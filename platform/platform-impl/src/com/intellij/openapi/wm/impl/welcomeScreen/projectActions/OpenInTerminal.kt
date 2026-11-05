// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProviderRecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem

private val TERMINAL_ACTION_IDS = listOf("Terminal.OpenInReworkedTerminal", "Terminal.OpenInTerminal")

/**
 * Opens the directory of the selected recent project in the terminal of the welcome screen project.
 *
 * The action runs the first available terminal action from [TERMINAL_ACTION_IDS].
 * It gives that action the welcome screen project and the directory of the selected recent project.
 */
internal class OpenInTerminal : RecentProjectsWelcomeScreenActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = findTerminalAction(event) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val [action, dataContext] = findTerminalAction(event) ?: return
    ActionUtil.performAction(action, event.withDataContext(dataContext))
  }

  private fun findTerminalAction(event: AnActionEvent): Pair<AnAction, DataContext>? {
    val project = getActiveProject() ?: return null
    val projectPath = getSelectedItem(event)?.getProjectPath() ?: return null
    val projectDir = LocalFileSystem.getInstance().findFileByPath(projectPath) ?: return null

    val dataContext = SimpleDataContext.builder().setParent(event.dataContext).add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, projectDir).build()

    for (actionId in TERMINAL_ACTION_IDS) {
      val action = event.actionManager.getAction(actionId) ?: continue
      val terminalEvent = AnActionEvent.createEvent(action, dataContext, null, event.place, event.uiKind, event.inputEvent)
      ActionUtil.updateAction(action, terminalEvent)
      if (terminalEvent.presentation.isEnabled) {
        event.presentation.icon = terminalEvent.presentation.icon ?: action.templatePresentation.icon
        return action to dataContext
      }
    }
    return null
  }
}

private fun RecentProjectTreeItem.getProjectPath(): String? {
  return when (this) {
    is RecentProjectItem -> projectPath
    is ProviderRecentProjectItem -> projectPath
    else -> null
  }
}
