// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.wsl.WslPath.Companion.parseWindowsUncPath
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.util.io.toNioPathOrNull
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
import kotlin.io.path.createTempFile
import kotlin.io.path.readText

object GradleLibraryDownloader {

  private val LOG = Logger.getInstance(GradleLibraryDownloader::class.java)
  private val GRADLE_5_6 = GradleVersion.version("5.6")
  private const val INIT_SCRIPT_FILE_PREFIX = "ijDownloadLibrary"

  /**
   * Download a Jar file with specified artifact coordinates by using the Gradle task executed on a specific Gradle module.
   * @param project associated project.
   * @param executionName execution name.
   * @param artifactNotation artifact coordinates in standard artifact format like `group:artifactId:version:classifier:anything:else`.
   * @param externalProjectPath path to the directory with the Gradle module that should be used to execute the task.
   */
  @JvmStatic
  fun downloadLibrary(
    project: Project,
    executionName: @Nls String,
    artifactNotation: String,
    externalProjectPath: String,
  ): CompletableFuture<Path> {
    val taskName = "ijDownloadLibrary" + UUID.randomUUID().toString().substring(0, 12)
    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = executionName
      it.externalProjectPath = externalProjectPath
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).gradleVmOptions
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val taskOutputFile = createTempFile("gradleDownloadLibrary")
    val userData = prepareUserData(artifactNotation, taskName, taskOutputFile, externalProjectPath)
    val resultWrapper = CompletableFuture<Path>()
    val listener = object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        try {
          val downloadedArtifactPath = Path.of(taskOutputFile.readText())
          if (!isValidJar(downloadedArtifactPath)) {
            GradleLog.LOG.warn("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
            FileUtil.delete(taskOutputFile)
            resultWrapper.completeExceptionally(IllegalStateException("Incorrect file header: $downloadedArtifactPath."))
            return
          }
          resultWrapper.complete(downloadedArtifactPath)
          FileUtil.delete(taskOutputFile)
        }
        catch (e: IOException) {
          GradleLog.LOG.warn(e)
          resultWrapper.completeExceptionally(e)
          return
        }
      }

      override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        FileUtil.delete(taskOutputFile)
        resultWrapper.completeExceptionally(IllegalStateException("Unable to download artifact."))
        GradleDependencySourceDownloaderErrorHandler.handle(project = project,
                                                            externalProjectPath = externalProjectPath,
                                                            artifact = artifactNotation,
                                                            exception = exception
        )
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
    return pathToNormalize.toNioPathOrNull()?.toCanonicalPath()
           ?: throw IllegalStateException("Unable to convert $this to canonical path")
  }
}