// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.sandbox

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ComposeSandboxStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (PropertiesComponent.getInstance(project).getBoolean(REOPEN_ON_START_PROPERTY, false)) {
      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(ComposeSandboxVirtualFile(), true)
      }
    }
  }
}
