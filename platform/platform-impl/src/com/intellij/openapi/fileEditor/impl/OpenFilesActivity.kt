// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.startup.InitProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OpenFilesActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    ProgressManager.getInstance().progressIndicator?.text = IdeBundle.message("progress.text.reopening.files")

    val fileEditorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
    withContext(Dispatchers.EDT) {
      fileEditorManager.init()
    }

    val editorSplitters = fileEditorManager.mainSplitters
    val panel = editorSplitters.restoreEditors()
    (project as ProjectEx).coroutineScope.launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      panel?.let(editorSplitters::doOpenFiles)
      fileEditorManager.initDockableContentFactory()
      EditorsSplitters.stopOpenFilesActivity(project)
      if (!fileEditorManager.hasOpenFiles() && !isNotificationSilentMode(project)) {
        project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true)
        if (AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
          RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart") {
            findAndOpenReadme(project)
          }
        }
      }
    }
  }
}

private fun findAndOpenReadme(project: Project) {
  val readme = project.guessProjectDir()?.findChild("README.md") ?: return
  if (!readme.isDirectory) {
    ApplicationManager.getApplication().invokeLater({ TextEditorWithPreview.openPreviewForFile(project, readme) }, project.disposed)
  }
}