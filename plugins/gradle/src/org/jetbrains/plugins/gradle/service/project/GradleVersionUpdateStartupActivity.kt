// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater

private class GradleVersionUpdateStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    }
  }
}