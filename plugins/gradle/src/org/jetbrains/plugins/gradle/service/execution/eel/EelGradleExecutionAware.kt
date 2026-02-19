// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.eel

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionAware
import org.jetbrains.plugins.gradle.service.execution.LocalBuildLayoutParameters
import org.jetbrains.plugins.gradle.service.execution.LocalGradleExecutionAware
import java.nio.file.Path

@ApiStatus.Internal
class EelGradleExecutionAware : GradleExecutionAware {

  override fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project,
  ) {
    // nothing to do
  }

  /**
   * This method is used ONLY for Gradle debugging purposes.
   * We are using proxy to proxy all debugger requests to the debugging agent, so, we're always dealing with the local environment.
   * Inside a container/WSL, the target address is always 127.0.0.1:%any free WSL/Docker port%.
   * On the local side the target address is always 127.0.0.1:%proxied port to the remote%.
   * So, [isRemoteRun] should always return `false`.
   */
  override fun isRemoteRun(runConfiguration: ExternalSystemRunConfiguration, project: Project): Boolean = false

  override fun getEnvironmentConfigurationProvider(
    projectPath: String,
    isPreviewMode: Boolean,
    project: Project,
  ): TargetEnvironmentConfigurationProvider? {
    return if (project.isEelSyncAvailable()) {
      runBlockingCancellable {
        EelTargetEnvironmentConfigurationProvider(project.getEelDescriptor().toEelApi(), project)
      }
    }
    else {
      null
    }
  }

  override fun getEnvironmentConfigurationProvider(
    runConfiguration: ExternalSystemRunConfiguration,
    project: Project,
  ): TargetEnvironmentConfigurationProvider? {
    return if (project.isEelSyncAvailable()) {
      runBlockingCancellable {
        EelTargetEnvironmentConfigurationProvider(project.getEelDescriptor().toEelApi(), project)
      }
    }
    else {
      null
    }
  }

  override fun isGradleInstallationHomeDir(project: Project, homePath: Path): Boolean {
    return LocalGradleExecutionAware().isGradleInstallationHomeDir(project, homePath)
  }

  override fun getDefaultBuildLayoutParameters(project: Project): BuildLayoutParameters? {
    return LocalBuildLayoutParameters(project, null)
  }

  override fun getBuildLayoutParameters(project: Project, projectPath: Path): BuildLayoutParameters? {
    return LocalBuildLayoutParameters(project, projectPath)
  }

  private fun Project.isEelSyncAvailable(): Boolean {
    return projectFilePath != null && getEelDescriptor() !is LocalEelDescriptor
  }
}