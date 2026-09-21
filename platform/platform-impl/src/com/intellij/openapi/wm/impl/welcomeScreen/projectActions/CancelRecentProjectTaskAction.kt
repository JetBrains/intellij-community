// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTaskTracker

/**
 * Cancels the background task running on a recent project the welcome screen holds.
 *
 * Such a project has no frame, so its progress is shown on its recent project row, and this is the cancel button of that progress. It
 * shows only while the row runs a task that can be cancelled.
 */
internal class CancelRecentProjectTaskAction : RecentProjectsWelcomeScreenActionBase() {
  private val taskTracker = RecentProjectTaskTracker.getInstance()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = runningTaskIsCancellable(event)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event) as? RecentProjectItem ?: return
    taskTracker.cancelRunningTask(item)
  }

  private fun runningTaskIsCancellable(event: AnActionEvent): Boolean {
    val item = getSelectedItem(event) as? RecentProjectItem ?: return false
    return taskTracker.runningTask(item)?.cancellable == true
  }
}
