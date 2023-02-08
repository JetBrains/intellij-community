// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun restoreOpenedFiles(fileEditorManager: FileEditorManagerImpl,
                                        editorSplitters: EditorsSplitters,
                                        project: Project,
                                        frameHelper: ProjectFrameHelper) {
  val hasOpenFiles = withContext(ModalityState.any().asContextElement()) {
    editorSplitters.restoreEditors(requestFocus = true)

    withContext(Dispatchers.EDT) {
      fileEditorManager.initDockableContentFactory()
      fileEditorManager.hasOpenFiles()
    }
  }

  if (!hasOpenFiles) {
    EditorsSplitters.stopOpenFilesActivity(project)
  }

  if (!hasOpenFiles && !isNotificationSilentMode(project)) {
    project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true)
    findAndOpenReadmeIfNeeded(project)
  }

  // later
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    project.getUserData(ProjectImpl.CREATION_TIME)?.let { startTime ->
      LifecycleUsageTriggerCollector.onProjectOpenFinished(project, TimeoutUtil.getDurationMillis(startTime), frameHelper.isTabbedWindow())
    }
  }
}

private fun findAndOpenReadmeIfNeeded(project: Project) {
  if (AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
    RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart") {
      val readme = project.guessProjectDir()?.findChild("README.md") ?: return@runOnceForProject
      if (!readme.isDirectory) {
        ApplicationManager.getApplication().invokeLater({ TextEditorWithPreview.openPreviewForFile(project, readme) }, project.disposed)
      }
    }
  }
}