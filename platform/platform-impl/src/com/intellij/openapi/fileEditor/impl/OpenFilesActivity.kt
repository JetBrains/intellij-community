// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.startup.InitProjectActivity

internal class OpenFilesActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    ProgressManager.getInstance().progressIndicator?.text = IdeBundle.message("progress.text.reopening.files")

    val fileEditorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
    val editorSplitters = fileEditorManager.mainSplitters
    val panel = editorSplitters.restoreEditors()
    ApplicationManager.getApplication().invokeLater({
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
                                                    }, project.disposed)
  }
}

private fun findAndOpenReadme(project: Project) {
  val dir = project.guessProjectDir()
  if (dir != null) {
    val readme = dir.findChild("README.md")
    if (readme != null && !readme.isDirectory) {
      ApplicationManager.getApplication().invokeLater(
        { TextEditorWithPreview.openPreviewForFile(project, readme) }, project.disposed)
    }
  }
}
