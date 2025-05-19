// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.GradleJavaCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.loadDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

object GradleArtifactDownloader {

  /**
   * Download a Jar file with specified artifact coordinates by using the Gradle task executed on a specific Gradle module.
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param projectPath path to the directory with the Gradle module that should be used to execute the task.
   */
  @JvmStatic
  fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    projectPath: String,
  ): CompletableFuture<Path> {
    return project.gradleCoroutineScope.async {
      downloadArtifactImpl(project, executionName, artifactNotation, projectPath)
    }.asCompletableFuture()
  }

  private suspend fun downloadArtifactImpl(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    projectPath: String,
  ): Path {
    val eel = project.getEelDescriptor().upgrade()
    val taskOutputEelPath = createTaskOutputFile(eel)
    val taskOutputPath = taskOutputEelPath.asNioPath()
    try {
      val projectEelPath = projectPath.toEelPath(eel)
      val taskName = "ijDownloadArtifact" + UUID.randomUUID().toString().substring(0, 12)
      val initScript = loadDownloadArtifactInitScript(artifactNotation, taskName, taskOutputEelPath, projectEelPath)
      val future = CompletableFuture<Nothing?>()
      ExternalSystemUtil.runTask(
        TaskExecutionSpec.create()
          .withProject(project)
          .withSystemId(GradleConstants.SYSTEM_ID)
          .withSettings(ExternalSystemTaskExecutionSettings().also {
            it.executionName = executionName
            it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
            it.externalProjectPath = projectPath
            it.taskNames = listOf(taskName)
          })
          .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          .withUserData(UserDataHolderBase().apply {
            putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, initScript)
          })
          .withResultListener(future)
          .withActivateToolWindowBeforeRun(false)
          .withActivateToolWindowOnFailure(false)
          .build()
      )
      future.await()
      val downloadedArtifactPath = taskOutputPath.readText().toEelPath(eel).asNioPath()
      if (!isValidJar(downloadedArtifactPath)) {
        throw IllegalStateException("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
      }
      return downloadedArtifactPath
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      GradleDependencySourceDownloaderErrorHandler.handle(project, projectPath, artifactNotation, e)
      throw IllegalStateException("Unable to download artifact.", e)
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

  private fun TaskExecutionSpecBuilder.withResultListener(future: CompletableFuture<Nothing?>) = withListener(
    object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        future.complete(null)
      }

      override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        future.cancel(true)
      }

      override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        future.completeExceptionally(exception)
      }
    }
  )
}