// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.ide.documentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.idea.devkit.documentation.DocumentationContentProvider

internal class InitDescriptorDocumentationsDownloadActivity : ProjectActivity {

  init {
    val application = ApplicationManager.getApplication()
    if (application.isHeadlessEnvironment || application.isCommandLine) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    DocumentationContentProvider.getInstance().initializeContentDownload()
  }
}
