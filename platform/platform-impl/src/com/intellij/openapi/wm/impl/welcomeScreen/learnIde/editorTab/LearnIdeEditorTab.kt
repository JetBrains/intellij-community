// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.editorTab

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnTabPanel
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LearnIdeEditorTab {
  fun openLearnIdeInEditorTab(project: Project) {
    runWithModalProgressBlocking(project, JBAcademyWelcomeScreenBundle.message("welcome.editor.tab.open.progress")) {
      withContext(Dispatchers.EDT) {
        val settingsFile = LearnIdeVirtualFile(LearnTabPanel(project), project)
        val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
        val options = FileEditorOpenOptions(reuseOpen = true, isSingletonEditorInWindow = true, selectAsCurrent = true)
        fileEditorManager.openFile(settingsFile, options)
      }
    }
  }
}
