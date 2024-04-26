// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
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
import java.util.UUID
import java.util.concurrent.CompletableFuture

object GradleDependencySourceDownloader {

  private val LOG = Logger.getInstance(GradleDependencySourceDownloader::class.java)
  private val GRADLE_5_6 = GradleVersion.version("5.6")
  private const val INIT_SCRIPT_FILE_PREFIX = "ijDownloadSources"

  @JvmStatic
  fun downloadSources(project: Project, executionName: @Nls String, sourceArtifactNotation: String, externalProjectPath: Path)
    : CompletableFuture<File> {
    var sourcesLocationFile: File
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
      it.externalProjectPath = externalProjectPath.toCanonicalPath()
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).getGradleVmOptions()
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val userData = prepareUserData(sourceArtifactNotation, taskName, sourcesLocationFile.toPath(), externalProjectPath)
    val resultWrapper = CompletableFuture<File>()
    val callback = object : TaskCallback {
      override fun onSuccess() {
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

      override fun onFailure() {
        resultWrapper.completeExceptionally(IllegalStateException("Unable to download sources."))
        val title = GradleBundle.message("gradle.notifications.sources.download.failed.title")
        val message = GradleBundle.message("gradle.notifications.sources.download.failed.content", sourceArtifactNotation)
        val notification = NotificationData(title, message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC)
        notification.setBalloonNotification(true)
        ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification)
      }
    }
    ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
                               callback, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false, userData)
    return resultWrapper
  }

  private fun prepareUserData(sourceArtifactNotation: String, taskName: String, sourcesLocationFilePath: Path, externalProjectPath: Path)
    : UserDataHolderBase {
    val legacyInitScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadLegacyDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath, externalProjectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 > it }
    )
    val initScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath, externalProjectPath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 <= it }
    )
    return UserDataHolderBase().apply {
      putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, listOf(legacyInitScript, initScript))
    }
  }
}