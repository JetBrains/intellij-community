// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.wsl.WslPath.Companion.parseWindowsUncPath
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.service.execution.loadDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.execution.loadLegacyDownloadArtifactInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.readText

object GradleArtifactDownloader {

  private val GRADLE_5_6 = GradleVersion.version("5.6")
  private const val INIT_SCRIPT_FILE_PREFIX = "ijArtifactDownloader"

  /**
   * Download a Jar file with specified artifact coordinates by using the Gradle task executed on a specific Gradle module.
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param externalProjectPath path to the directory with the Gradle module that should be used to execute the task.
   */
  @JvmStatic
  suspend fun downloadArtifact(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    externalProjectPath: String,
  ): CompletableFuture<Path> {
    val taskName = "ijDownloadArtifact" + UUID.randomUUID().toString().substring(0, 12)
    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = executionName
      it.externalProjectPath = externalProjectPath
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).gradleVmOptions
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val eelDescriptor = project.getEelDescriptor()
    val eel = eelDescriptor.upgrade()
    val absoluteTaskOutputFileEelPath = createTaskOutputFile(eel)
    val userData = prepareUserData(
      artifactNotation,
      taskName,
      absoluteTaskOutputFileEelPath.asCanonicalResolvedPath(eel),
      externalProjectPath
    )
    val resultWrapper = CompletableFuture<Path>()
    val taskOutputFile = absoluteTaskOutputFileEelPath.asNioPath()
    val listener = object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        try {
          val downloadedArtifactPath = taskOutputFile.readText().asResolvedAbsolutePath(eel)
          if (!isValidJar(downloadedArtifactPath)) {
            GradleLog.LOG.warn("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
            FileUtil.delete(taskOutputFile)
            resultWrapper.completeExceptionally(IllegalStateException("Incorrect file header: $downloadedArtifactPath."))
            return
          }
          FileUtil.delete(taskOutputFile)
          resultWrapper.complete(downloadedArtifactPath)
        }
        catch (e: IOException) {
          GradleLog.LOG.warn(e)
          resultWrapper.completeExceptionally(e)
          return
        }
      }

      override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        FileUtil.delete(taskOutputFile)
        GradleDependencySourceDownloaderErrorHandler.handle(project = project,
                                                            externalProjectPath = externalProjectPath,
                                                            artifact = artifactNotation,
                                                            exception = exception
        )
        resultWrapper.completeExceptionally(IllegalStateException("Unable to download artifact."))
      }
    }
    val spec = TaskExecutionSpec.create(project, GradleConstants.SYSTEM_ID, DefaultRunExecutor.EXECUTOR_ID, settings)
      .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      .withListener(listener)
      .withUserData(userData)
      .withActivateToolWindowBeforeRun(false)
      .withActivateToolWindowOnFailure(false)
      .build()
    ExternalSystemUtil.runTask(spec)
    return resultWrapper
  }

  private fun prepareUserData(artifactNotation: String, taskName: String, taskOutputPath: Path, externalProjectPath: String)
    : UserDataHolderBase {
    val projectPath = externalProjectPath.asSystemDependentGradleProjectPath()
    val legacyInitScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadLegacyDownloadArtifactInitScript(artifactNotation, taskName, taskOutputPath, projectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 > it }
    )
    val initScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadDownloadArtifactInitScript(artifactNotation, taskName, taskOutputPath, projectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 <= it }
    )
    return UserDataHolderBase().apply {
      putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, listOf(legacyInitScript, initScript))
    }
  }

  private fun String.asSystemDependentGradleProjectPath(): String {
    val wslPath = parseWindowsUncPath(this)
    val pathToNormalize = wslPath?.linuxPath ?: this
    return Path.of(pathToNormalize).toCanonicalPath()
  }

  private suspend fun createTaskOutputFile(eel: EelApi): EelPath {
    val options = EelFileSystemApi.CreateTemporaryEntryOptions.Builder()
      .prefix("ijDownloadArtifactOut")
      .build()
    return eel.fs.createTemporaryFile(options)
      .getOrThrow { IllegalStateException("Unable to create the task output file: ${it.message}") }
  }

  private suspend fun EelPath.asCanonicalResolvedPath(eel: EelApi): Path {
    // if the execution is taking place on a local machine, we could use the path as is
    if (eel is LocalEelApi) {
      return this.asNioPath()
    }
    // in the case of executing on a WSL, we have to operate both local and relative WSL paths to be able to use the path inside the
    // Gradle task
    val resolvedPath = eel.fs.canonicalize(this)
      .getOrThrow { java.lang.IllegalStateException("Unable to canonicalize the task output file path: ${it.message}") }

    // We have to use `toString()` to prevent the path from being converted into an absolute one:
    //  - resolvedPath.asNioPath() - nio.Path: `\\wsl$\something\something`;
    //  - Path.of(resolvedPath.toString()) - nio.Path: `/something/something`;
    return Path.of(resolvedPath.toString())
  }

  private fun String.asResolvedAbsolutePath(eel: EelApi): Path {
    val eelPath = eel.fs.getPath(this)
    return eelPath.asNioPath()
  }
}