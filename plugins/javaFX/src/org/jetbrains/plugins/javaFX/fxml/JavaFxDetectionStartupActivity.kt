// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class JavaFxDetectionStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    smartReadAction(project) {
      // Avoids a freeze on first use of Java intentions
      populateCachedJavaFxModules(project)
    }
  }

  private fun populateCachedJavaFxModules(project: Project) {
    if (!project.isDisposed && project.isOpen) {
      JavaFxModuleUtil.hasJavaFxArtifacts(project)
      JavaFxModuleUtil.getCachedJavaFxModules(project)
    }
  }
}