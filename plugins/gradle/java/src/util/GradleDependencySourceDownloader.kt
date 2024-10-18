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
import org.jetbrains.plugins.gradle.service.execution.loadDownloadSourcesInitScript
import org.jetbrains.plugins.gradle.service.execution.loadLegacyDownloadSourcesInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture

object GradleDependencySourceDownloader {

  private val LOG = Logger.getInstance(GradleDependencySourceDownloader::class.java)
  private val GRADLE_5_6 = GradleVersion.version("5.6")
  private const val INIT_SCRIPT_FILE_PREFIX = "ijDownloadSources"

  @JvmStatic
  fun downloadSources(project: Project, executionName: @Nls String, sourceArtifactNotation: String, externalProjectPath: String)
    : CompletableFuture<File> {
    val sourcesLocationFile: File
    try {
      sourcesLocationFile = File(FileUtil.createTempDirectory("sources", "loc"), "path.tmp")
      Runtime.getRuntime().addShutdownHook(Thread({ FileUtil.delete(sourcesLocationFile) }, "GradleAttachSourcesProvider cleanup"))
    }
    catch (e: IOException) {
      LOG.warn(e)
      return CompletableFuture.failedFuture(e)
    }
    val taskName = "ijDownloadSources" + UUID.randomUUID().toString().substring(0, 12)
    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = executionName
      it.externalProjectPath = externalProjectPath
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).getGradleVmOptions()
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val userData = prepareUserData(sourceArtifactNotation, taskName, sourcesLocationFile.toPath(), externalProjectPath)
    val resultWrapper = CompletableFuture<File>()
    val listener = object : ExternalSystemTaskNotificationListener {
      override fun onSuccess(proojecPath: String, id: ExternalSystemTaskId) {
        val sourceJar: File
        try {
          val downloadedArtifactPath = Path.of(FileUtil.loadFile(sourcesLocationFile))
          if (!isValidJar(downloadedArtifactPath)) {
            GradleLog.LOG.warn("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
            FileUtil.delete(sourcesLocationFile)
            resultWrapper.completeExceptionally(IllegalStateException("Incorrect file header: $downloadedArtifactPath."))
            return
          }
          sourceJar = downloadedArtifactPath.toFile()
          FileUtil.delete(sourcesLocationFile)
        }
        catch (e: IOException) {
          GradleLog.LOG.warn(e)
          resultWrapper.completeExceptionally(e)
          return
        }
        resultWrapper.complete(sourceJar)
      }

      override fun onFailure(proojecPath: String, id: ExternalSystemTaskId, exception: Exception) {
        resultWrapper.completeExceptionally(IllegalStateException("Unable to download sources."))
        GradleDependencySourceDownloaderErrorHandler.handle(project = project,
                                                            externalProjectPath = externalProjectPath,
                                                            artifact = sourceArtifactNotation,
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

  private fun prepareUserData(sourceArtifactNotation: String, taskName: String, sourcesLocationFilePath: Path, externalProjectPath: String)
    : UserDataHolderBase {
    val projectPath = externalProjectPath.asSystemDependentGradleProjectPath()
    val legacyInitScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadLegacyDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath, projectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 > it }
    )
    val initScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath, projectPath) },
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