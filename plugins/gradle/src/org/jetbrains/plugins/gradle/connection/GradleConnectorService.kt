// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.util.function.Function

@ApiStatus.Internal
interface GradleConnectorService {

  fun getKnownGradleUserHomes(): Set<String>

  fun <R> withGradleConnection(
    projectPath: String,
    taskId: ExternalSystemTaskId?,
    executionSettings: GradleExecutionSettings?,
    listener: ExternalSystemTaskNotificationListener?,
    cancellationToken: CancellationToken?,
    function: Function<ProjectConnection, R>,
  ): R

  companion object {

    @JvmStatic
    fun getInstance(project: Project): GradleConnectorService {
      return project.service<GradleConnectorService>()
    }

    @JvmStatic
    @Deprecated("Use getInstance function with project instead")
    fun getInstance(projectPath: String, taskId: ExternalSystemTaskId?): GradleConnectorService {
      return taskId?.findProject()?.service<GradleConnectorService>()
             ?: findOpenProject(projectPath)?.service<GradleConnectorService>()
             ?: DefaultGradleConnectorService()
    }

    private fun findOpenProject(projectPath: String): Project? {
      for (openProject in ProjectUtil.getOpenProjects()) {
        val projectBasePath = openProject.basePath ?: continue
        if (FileUtil.isAncestor(projectBasePath, projectPath, false)) {
          return openProject
        }
      }
      return null
    }
  }
}