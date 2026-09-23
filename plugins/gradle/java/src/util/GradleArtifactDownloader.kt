// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.externalSystem.util.task.TaskExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.GradleJavaCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.loadDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

object GradleArtifactDownloader {

  @JvmStatic
  fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    projectPath: String,
  ): CompletableFuture<Path?> =
    downloadArtifact(project, executionName, artifactNotation, projectPath, DefaultGradleDependencySourceDownloaderErrorHandler)

  /**
   * Download a Jar file with specified artifact coordinates by using the Gradle task executed on a specific Gradle module.
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param projectPath path to the directory with the Gradle module that should be used to execute the task.
   * @param errorHandler an instance of GradleDependencySourceDownloaderErrorHandler responsible for handling errors during artifact download
   */
  @JvmStatic
  fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    projectPath: String,
    errorHandler: GradleDependencySourceDownloaderErrorHandler,
  ): CompletableFuture<Path?> =
    downloadArtifact(project, executionName, artifactNotation, projectPath, projectPath, { it }, errorHandler)

  /**
   * Downloads a Jar file with the specified artifact coordinates in the Gradle project of [moduleData].
   *
   * The task runs from [GradleModuleData.directoryToRunTask].
   * The artifact resolves with the repositories of the Gradle project in [GradleModuleData.gradleProjectDir].
   * This project can be a subproject or a project of an included build.
   *
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param moduleData the Gradle module whose repositories resolve the artifact.
   * @param errorHandler an instance of GradleDependencySourceDownloaderErrorHandler responsible for handling errors during artifact download
   */
  @ApiStatus.Internal
  @JvmStatic
  fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    moduleData: GradleModuleData,
    errorHandler: GradleDependencySourceDownloaderErrorHandler,
  ): CompletableFuture<Path?> {
    val identityPath = moduleData.gradleIdentityPathOrNull
    return downloadArtifact(
      project, executionName, artifactNotation,
      directoryToRunTask = moduleData.directoryToRunTask,
      gradleProjectPath = moduleData.gradleProjectDir,
      taskPath = { taskName -> if (identityPath == null) taskName else moduleData.getTaskPath(taskName) },
      errorHandler = errorHandler,
    )
  }

  private fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    directoryToRunTask: String,
    gradleProjectPath: String,
    taskPath: (String) -> String,
    errorHandler: GradleDependencySourceDownloaderErrorHandler,
  ): CompletableFuture<Path?> {
    return project.gradleCoroutineScope.async {
      try {
        downloadArtifactImpl(project, executionName, artifactNotation, directoryToRunTask, gradleProjectPath, taskPath)
      }
      catch (exception: Exception) {
        rethrowControlFlowException(exception)
        errorHandler.handle(project, gradleProjectPath, artifactNotation, exception)
        null
      }
    }.asCompletableFuture()
  }

  private suspend fun downloadArtifactImpl(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    directoryToRunTask: String,
    gradleProjectPath: String,
    taskPath: (String) -> String,
  ): Path {
    val eel = project.getEelDescriptor().toEelApi()
    val taskOutputEelPath = createTaskOutputFile(eel)
    val taskOutputPath = taskOutputEelPath.asNioPath()
    try {
      val projectEelPath = gradleProjectPath.toEelPath(eel)
      val taskName = "ijDownloadArtifact" + UUID.randomUUID().toString().substring(0, 12)
      val initScript = loadDownloadArtifactInitScript(artifactNotation, taskName, taskOutputEelPath, projectEelPath)

      TaskExecutionUtil.runTask(
        TaskExecutionSpec.create()
          .withProject(project)
          .withSystemId(GradleConstants.SYSTEM_ID)
          .withSettings(ExternalSystemTaskExecutionSettings().also {
            it.executionName = executionName
            it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
            it.externalProjectPath = directoryToRunTask
            it.taskNames = listOf(taskPath(taskName))
          })
          .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          .withUserData(UserDataHolderBase().apply {
            putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, initScript)
          })
          .withActivateToolWindowBeforeRun(false)
          .withActivateToolWindowOnFailure(false)
          .dontNavigateToError()
      )

      val downloadedArtifactPath = taskOutputPath.readText().toEelPath(eel).asNioPath()
      if (!isValidJar(downloadedArtifactPath)) {
        throw IllegalStateException("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
      }
      return downloadedArtifactPath
    }
    finally {
      taskOutputPath.deleteIfExists()
    }
  }

  private suspend fun createTaskOutputFile(eel: EelApi): EelPath {
    return eel.fs.createTemporaryFile()
      .prefix("ijDownloadArtifactOut")
      .getOrThrow { IllegalStateException("Unable to create the task output file: ${it.message}") }
  }

  private fun String.toEelPath(eel: EelApi): EelPath {
    return eel.fs.getPath(this)
  }
}