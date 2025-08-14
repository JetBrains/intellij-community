// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.IdeBundle.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.recentFiles.frontend.Switcher.SwitcherPanel
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

internal fun createAndShowNewSwitcher(event: AnActionEvent, project: Project) {
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewSwitcherSuspend(event, project)
  }
}

internal fun createAndShowNewRecentFiles(onlyEditedFiles: Boolean, project: Project) {
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewRecentFilesSuspend(onlyEditedFiles, project)
  }
}

private suspend fun createAndShowNewSwitcherSuspend(event: AnActionEvent, project: Project): SwitcherPanel {
  return createAndShow(
    onlyEditedFiles = null,
    event = event,
    title = message("window.title.switcher"),
    project = project,
  )
}

@ApiStatus.Internal
suspend fun createAndShowNewRecentFilesSuspend(onlyEditedFiles: Boolean, project: Project): SwitcherPanel {
  return createAndShow(
    onlyEditedFiles = onlyEditedFiles,
    event = null,
    title = message("title.popup.recent.files"),
    project = project,
  )
}

private suspend fun createAndShow(onlyEditedFiles: Boolean?, event: AnActionEvent?, @Nls title: String, project: Project): SwitcherPanel {
  val parameters = SwitcherLaunchEventParameters(event?.inputEvent)
  val remoteApi = FileSwitcherApi.getInstance()
  val frontendModel = FrontendRecentFilesModel.getInstanceAsync(project)

  return withContext(Dispatchers.EDT) {
    SwitcherPanel(project = project,
                  title = title,
                  launchParameters = parameters,
                  onlyEditedFiles = onlyEditedFiles,
                  frontendModel = frontendModel,
                  remoteApi = remoteApi)
  }
}
