// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.IdeBundle.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
  // Because the switcher is closed on the modifier key release,
  // it MUST receive this event, otherwise it won't be able to close (IJPL-195829).
  // But because of all this suspending code, the event may arrive before the switcher starts listening,
  // so we need to start capturing events RIGHT NOW and pass them to the switcher later.
  val eventCollectorDisposable = Disposer.newDisposable()
  val eventCollector = SwitcherKeyEventCollector(event, eventCollectorDisposable)
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewSwitcherSuspend(eventCollector, project)
  }.invokeOnCompletion {
    Disposer.dispose(eventCollectorDisposable)
  }
}

internal fun createAndShowNewRecentFiles(onlyEditedFiles: Boolean, project: Project) {
  RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope.launch {
    createAndShowNewRecentFilesSuspend(onlyEditedFiles, project)
  }
}

private suspend fun createAndShowNewSwitcherSuspend(eventCollector: SwitcherKeyEventCollector, project: Project): SwitcherPanel {
  return createAndShow(
    onlyEditedFiles = null,
    eventCollector = eventCollector,
    title = message("window.title.switcher"),
    project = project,
  )
}

@ApiStatus.Internal
suspend fun createAndShowNewRecentFilesSuspend(onlyEditedFiles: Boolean, project: Project): SwitcherPanel {
  return createAndShow(
    onlyEditedFiles = onlyEditedFiles,
    eventCollector = null,
    title = message("title.popup.recent.files"),
    project = project,
  )
}

private suspend fun createAndShow(
  onlyEditedFiles: Boolean?,
  eventCollector: SwitcherKeyEventCollector?,
  @Nls title: String,
  project: Project,
): SwitcherPanel {
  val parameters = SwitcherLaunchEventParameters(eventCollector?.initialEvent)
  val remoteApi = FileSwitcherApi.getInstance()
  val frontendModel = FrontendRecentFilesModel.getInstanceAsync(project)

  return withContext(Dispatchers.EDT) {
    SwitcherPanel(project = project,
                  title = title,
                  launchParameters = parameters,
                  alreadyReleasedKeys = eventCollector?.getKeyReleaseEventsSoFar(),
                  onlyEditedFiles = onlyEditedFiles,
                  frontendModel = frontendModel,
                  remoteApi = remoteApi)
  }
}
