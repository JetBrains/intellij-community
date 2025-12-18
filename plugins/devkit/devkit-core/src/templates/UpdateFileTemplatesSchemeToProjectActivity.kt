// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.templates

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.runOnceForProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.idea.devkit.DevKitBundle

internal class UpdateFileTemplatesSchemeToProjectActivity : ProjectActivity {
  init {
    val application = ApplicationManager.getApplication()
    if (application.isHeadlessEnvironment
        || application.isUnitTestMode
        || application.isCommandLine) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return

    runOnceForProject(project, "update.intellij.repository.file.templates.scheme") {
      writeCommandAction(project, DevKitBundle.message("command.name.update.file.templates.scheme")) {
        val manager = FileTemplateManager.getInstance(project)
        manager.setCurrentScheme(manager.getProjectScheme())
      }
    }
  }
}