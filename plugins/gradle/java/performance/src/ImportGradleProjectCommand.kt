// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.open.setupGradleSettings
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.SuggestGradleVersionOptions
import org.jetbrains.plugins.gradle.util.setupGradleJvm
import org.jetbrains.plugins.gradle.util.suggestGradleVersion
import org.jetbrains.plugins.gradle.util.validateJavaHome
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * The command imports each linked Gradle project and waits for the indexing.
 * The command links the project first if the project has no linked Gradle project.
 * Syntax: %importGradleProject
 */
class ImportGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  override fun getName(): String = NAME

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project)
    val currentAutoReloadType = projectTrackerSettings.autoReloadType
    projectTrackerSettings.autoReloadType = ExternalSystemProjectTrackerSettings.AutoReloadType.NONE
    try {
      context.message("Waiting for open and initialized Gradle project", line)
      awaitExternalProjectsManagerInitialization(project)
      project.waitForSmartMode()
      waitForCurrentResolveTasks(context, project)

      context.message("Import of the project has been started", line)
      val gradleSettings = GradleSettings.getInstance(project)
      try {
        linkGradleProjectIfNeeded(project, context, gradleSettings)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        throw IllegalStateException("Link of a gradle project failed. Not a gradle project. ${e.message}", e)
      }
      doGradleSync(project, context, gradleSettings)
    }
    finally {
      context.message("Import has been finished", line)
      projectTrackerSettings.autoReloadType = currentAutoReloadType
    }
    project.waitForSmartMode()
  }

  private suspend fun awaitExternalProjectsManagerInitialization(project: Project) {
    suspendCancellableCoroutine { continuation ->
      ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
        continuation.resume(Unit)
      }
    }
  }

  private suspend fun waitForCurrentResolveTasks(context: PlaybackContext, project: Project) {
    context.message("Waiting for current import resolve tasks", line)
    val processingManager = ExternalSystemProcessingManager.getInstance()
    while (processingManager.hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT, project)) {
      delay(100.milliseconds)
    }
    context.message("Import resolve tasks has been completed", line)
  }

  private suspend fun doGradleSync(project: Project, context: PlaybackContext, gradleSettings: GradleSettings) {
    val projectsSettings = gradleSettings.linkedProjectsSettings
    val projectsPaths: List<String?> = projectsSettings.map { it.externalProjectPath }
    val gradleProjectsToRefreshCount = AtomicInteger(projectsSettings.size)
    val projectsWithResolveErrors = StringBuilder()
    val importDeferred = CompletableDeferred<Unit>()
    for (settings in projectsSettings) {
      val importSpecBuilder = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
      importSpecBuilder
          .withImportProjectData(false)
          .withCallback(object : ExternalProjectRefreshCallback {

            override fun onSuccess(externalProject: DataNode<ProjectData>?) {
              context.message("Gradle resolve finished for: ${externalProject!!.data.linkedExternalProjectPath}", line)
              val connection: SimpleMessageBusConnection = project.messageBus.simpleConnect()
              connection.subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
                override fun onFinalTasksFinished(projectPath: String?) {
                  handleImportFinished(projectPath)
                }

                override fun onImportFailed(projectPath: String?, failure: Throwable) {
                  handleImportFinished(projectPath)
                }

                private fun handleImportFinished(projectPath: String?) {
                  if (projectPath !in projectsPaths) return
                  connection.disconnect()
                  if (gradleProjectsToRefreshCount.decrementAndGet() == 0) {
                    ApplicationManager.getApplication().invokeLater {
                      importDeferred.complete(Unit)
                    }
                  }
                }
              })

              ProjectDataManager.getInstance().importData(externalProject, project)
            }

            override fun onFailure(errorMessage: String, errorDetails: String?) {
              context.error("Gradle resolve failed for: ${settings.externalProjectPath}:$errorMessage:$errorDetails", line)
              synchronized(projectsWithResolveErrors) {
                if (projectsWithResolveErrors.isNotEmpty()) {
                  projectsWithResolveErrors.append(", ")
                }
                projectsWithResolveErrors.append("'${Paths.get(settings.externalProjectPath!!).fileName?.toString() ?: ""}'")
              }
              if (gradleProjectsToRefreshCount.decrementAndGet() == 0) {
                ApplicationManager.getApplication().invokeLater {
                  importDeferred.completeExceptionally(IllegalStateException(projectsWithResolveErrors.toString()))
                }
              }
            }
          })
        ExternalSystemUtil.refreshProject(settings.externalProjectPath, importSpecBuilder)
      }
      importDeferred.await()
  }

  companion object {
    const val NAME: String = "importGradleProject"
    const val PREFIX: String = "$CMD_PREFIX$NAME"

    suspend fun linkGradleProjectIfNeeded(
      project: Project,
      context: PlaybackContext,
      gradleSettings: GradleSettings,
    ) {
      if (gradleSettings.linkedProjectsSettings.isNotEmpty()) return
      val projectDir = project.guessProjectDir()!!
      val children = projectDir.children
      val isGradleProject = children.any { GradleConstants.KNOWN_GRADLE_FILES.contains(it.name) }
      if (!isGradleProject) {
        context.error("Unable to find Gradle project at ${projectDir.path}", 0)
        context.message("Files found at the path: ${children.joinToString(prefix = "[", postfix = "]") { it.name }}", 0)
        throw IllegalStateException("Unable to find Gradle project at ${projectDir.path}")
      }

      val projectSettings = GradleDefaultProjectSettings.createProjectSettings(projectDir.path)
      gradleSettings.setupGradleSettings()
      val gradleVersion: GradleVersion? = suggestGradleVersion(
        SuggestGradleVersionOptions()
          .withProject(project)
          .withProjectJdkVersionFilter(project)
      )
      if (gradleVersion != null) {
        setupGradleJvm(project, projectSettings, gradleVersion)
        validateJavaHome(project, projectDir.toNioPath(), gradleVersion)
      }

      withContext(Dispatchers.EDT) {
        gradleSettings.linkProject(projectSettings)
      }
    }
  }
}
