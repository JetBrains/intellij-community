// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.configuration.HeadlessLogging
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationListener
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationManager
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsTaskId
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.div

class GradleHeadlessLoggingProjectActivity(val scope: CoroutineScope) : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!application.isHeadlessEnvironment || application.isUnitTestMode) {
      return
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    addTaskNotificationListener(progressManager)
    addStateNotificationListener(project, progressManager)
    addAnnotationListener()
  }

  private fun addTaskNotificationListener(progressManager: ExternalSystemProgressNotificationManager) {
    val listener = LoggingNotificationListener()
    progressManager.addNotificationListener(listener)
    scope.launch {
      awaitCancellationAndInvoke {
        progressManager.removeNotificationListener(listener)
      }
    }
  }

  private fun addStateNotificationListener(project: Project, progressManager: ExternalSystemProgressNotificationManager) {
    val notificationListener = StateNotificationListener(project, scope)
    progressManager.addNotificationListener(notificationListener)
    scope.launch {
      awaitCancellationAndInvoke {
        progressManager.removeNotificationListener(notificationListener)
      }
    }
  }

  private fun addAnnotationListener() {
    val externalAnnotationsNotificationManager = ExternalAnnotationsProgressNotificationManager.getInstance()
    val externalAnnotationsProgressListener = StateExternalAnnotationNotificationListener()

    externalAnnotationsNotificationManager.addNotificationListener(externalAnnotationsProgressListener)
    scope.launch {
      awaitCancellationAndInvoke {
        externalAnnotationsNotificationManager.removeNotificationListener(externalAnnotationsProgressListener)
      }
    }
  }

  class StateExternalAnnotationNotificationListener : ExternalAnnotationsProgressNotificationListener {

    override fun onStartResolve(id: ExternalAnnotationsTaskId) {
      HeadlessLogging.logMessage(gradlePrefix + "Gradle resolving external annotations started ${id.projectId}")
    }

    override fun onFinishResolve(id: ExternalAnnotationsTaskId) {
      HeadlessLogging.logMessage(gradlePrefix + "Gradle resolving external annotations finished ${id.projectId}")
    }
  }

  class StateNotificationListener(
    private val project: Project, private val scope: CoroutineScope
  ) : ExternalSystemTaskNotificationListener {

    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      HeadlessLogging.logMessage(gradlePrefix + "Gradle resolve stage finished with success: ${id.ideProjectId}")

      project.messageBus.connect(scope)
        .subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
          override fun onImportStarted(projectPath: String?) {
            HeadlessLogging.logMessage(gradlePrefix + "Gradle data import stage started: ${id.ideProjectId}")
          }

          override fun onImportFinished(projectPath: String?) {
            HeadlessLogging.logMessage(gradlePrefix + "Gradle data import stage finished with success: ${id.ideProjectId}")
          }

          override fun onFinalTasksFinished(projectPath: String?) {
            HeadlessLogging.logMessage(gradlePrefix + "Gradle data import(final tasks) stage finished: ${id.ideProjectId}")
          }

          override fun onFinalTasksStarted(projectPath: String?) {
            HeadlessLogging.logMessage(gradlePrefix + "Gradle data import(final tasks) stage started: ${id.ideProjectId}")
          }

          override fun onImportFailed(projectPath: String?, t: Throwable) {
            HeadlessLogging.logFatalError(t)
          }
        })
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
      if (!id.isGradleProjectResolveTask()) return
      HeadlessLogging.logFatalError(exception)
    }

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      HeadlessLogging.logWarning(gradlePrefix + "Gradle resolve stage canceled ${id.ideProjectId}")
    }

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      HeadlessLogging.logMessage(gradlePrefix + "Gradle resolve stage started ${id.ideProjectId}, working dir: $projectPath")
    }
  }

  class LoggingNotificationListener : ExternalSystemTaskNotificationListener {

    private val logPath = try {
      gradleLogWriterPath.createParentDirectories().createFile()
    }
    catch (e: java.nio.file.FileAlreadyExistsException) {
      gradleLogWriterPath
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      val gradleText = (if (stdOut) "" else "STDERR: ") + text
      logPath.appendText(gradleText)
      val croppedMessage = processMessage(gradleText)
      if (croppedMessage != null) {
        HeadlessLogging.logMessage(gradlePrefix + croppedMessage)
      }
    }

    private fun processMessage(gradleText: String): String? {
      val cropped = gradleText.trimStart('\r').trimEnd('\n')
      if (cropped.startsWith("Download")) {
        // we don't want to be flooded by a ton of download messages, so we'll print only final message
        if (cropped.contains(" took ")) {
          return cropped
        }
        return null
      }
      return cropped
    }
  }

}

internal fun ExternalSystemTaskId.isGradleProjectResolveTask() = this.projectSystemId == GradleConstants.SYSTEM_ID &&
                                                                 this.type == ExternalSystemTaskType.RESOLVE_PROJECT

private val gradleLogWriterPath = Path.of(PathManager.getLogPath()) / "gradle-import.log"

private val gradlePrefix = "[Gradle]: "