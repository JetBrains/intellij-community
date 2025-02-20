@file:JvmName("ImportGradleProjectUtil")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings.AlreadyImportedProjectException
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.project.open.setupGradleSettings
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings.Companion.createProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

@VisibleForTesting
internal fun importProject(project: Project) {
  runBlockingMaybeCancellable {
    suspendCancellableCoroutine { continuation ->
      ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
        continuation.resumeWith(Result.success(Unit))
      }
    }

    edtWriteAction {
      val projectDir = project.guessProjectDir()
      if (projectDir == null) throw IllegalStateException("No project dir")

      val gradleSettings = GradleSettings.getInstance(project)
      gradleSettings.setupGradleSettings()

      try {
        val projectSettings = createProjectSettings(projectDir.getPath())
        projectSettings.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK

        gradleSettings.linkProject(projectSettings)
      }
      catch (ignored: AlreadyImportedProjectException) {
        // make sure Gradle uses project JDK
        val linked = GradleSettings.getInstance(project).getLinkedProjectSettings(projectDir.getPath())
        linked?.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
      }
    }

    edtWriteAction {
      ExternalSystemUtil.refreshProjects(ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
    }
  }
}