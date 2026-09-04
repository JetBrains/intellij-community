// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.util.DisposeAwareRunnable
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.concurrency.toPromise
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

class ImportGradleProjectCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    runWhenGradleImportAndIndexingFinished(context, actionCallback)
    return actionCallback.toPromise()
  }

  private fun runWhenGradleImportAndIndexingFinished(context: PlaybackContext, callback: ActionCallback) {
    val project = context.project
    val projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project)
    val currentAutoReloadType = projectTrackerSettings.autoReloadType
    projectTrackerSettings.autoReloadType = ExternalSystemProjectTrackerSettings.AutoReloadType.NONE
    context.message("Waiting for open and initialized Gradle project", line)
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
      DumbService.getInstance(project).runWhenSmart {
        ApplicationManager.getApplication().executeOnPooledThread {
          waitForCurrentResolveTasks(context, project)
            .thenAsync {
              context.message("Import of the project has been started", line)
              val promise = AsyncPromise<Void?>()
              val gradleSettings = GradleSettings.getInstance(project)
              linkGradleProjectIfNeeded(project, context, gradleSettings)
                .onError { callback.reject("Link of a gradle project failed. Not a gradle project") }
                .onSuccess { doGradleSync(project, context, promise, gradleSettings, callback) }
              promise
            }
            .onProcessed {
              context.message("Import has been finished", line)
              projectTrackerSettings.autoReloadType = currentAutoReloadType
              DumbService.getInstance(project).runWhenSmart(
                DisposeAwareRunnable.create({ callback.setDone() }, project)
              )
            }
        }
      }
    }
  }

  private fun waitForCurrentResolveTasks(context: PlaybackContext, project: Project): Promise<*> {
    val promise = AsyncPromise<Any?>()
    context.message("Waiting for current import resolve tasks", line)
    ApplicationManager.getApplication().executeOnPooledThread {
      val processingManager = ExternalSystemProcessingManager.getInstance()
      while (processingManager.hasTaskOfTypeInProgress(ExternalSystemTaskType.RESOLVE_PROJECT, project)) {
        try {
          Thread.sleep(100)
        }
        catch (_: InterruptedException) {
        }
      }
      promise.setResult(null)
    }
    return promise.onProcessed {
      context.message("Import resolve tasks has been completed", line)
    }
  }

  @Suppress("DEPRECATION")
  private fun doGradleSync(
    project: Project,
    context: PlaybackContext,
    promise: AsyncPromise<Void?>,
    gradleSettings: GradleSettings,
    callback: ActionCallback,
  ) {
    val projectsSettings = gradleSettings.linkedProjectsSettings
    val projectsPaths: List<String?> = projectsSettings.map { it.externalProjectPath }
    val gradleProjectsToRefreshCount = AtomicInteger(projectsSettings.size)
    val projectsWithResolveErrors = StringBuilder()
    for (settings in projectsSettings) {
      val importSpecBuilder = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
      importSpecBuilder.callback(object : ExternalProjectRefreshCallback {
        private val defaultCallback = ImportSpecBuilder.DefaultProjectRefreshCallback(importSpecBuilder.build())

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
                  promise.setResult(null)
                }
              }
            }
          })

          defaultCallback.onSuccess(externalProject)
          callback.setDone()
        }

        override fun onFailure(errorMessage: String, errorDetails: String?) {
          context.error("Gradle resolve failed for: ${settings.externalProjectPath}:$errorMessage:$errorDetails", line)
          synchronized(projectsWithResolveErrors) {
            if (projectsWithResolveErrors.isNotEmpty()) {
              projectsWithResolveErrors.append(", ")
            }
            projectsWithResolveErrors.append("'${Paths.get(settings.externalProjectPath!!).fileName?.toString() ?: ""}'")
          }
          defaultCallback.onFailure(errorMessage, errorDetails)
          if (gradleProjectsToRefreshCount.decrementAndGet() == 0) {
            ApplicationManager.getApplication().invokeLater {
              promise.setError(projectsWithResolveErrors.toString())
            }
            callback.reject("Gradle sync failed")
          }
        }
      })
      ExternalSystemUtil.refreshProject(settings.externalProjectPath, importSpecBuilder)
    }
  }

  companion object {
    const val PREFIX: String = "%importGradleProject"

    @JvmStatic
    fun linkGradleProjectIfNeeded(
      project: Project,
      context: PlaybackContext,
      gradleSettings: GradleSettings,
    ): Promise<Void?> {
      if (gradleSettings.linkedProjectsSettings.isEmpty()) {
        val projectDir = project.guessProjectDir()!!
        val children = projectDir.children
        val isGradleProject = children.any { GradleConstants.KNOWN_GRADLE_FILES.contains(it.name) }
        if (!isGradleProject) {
          context.error("Unable to find Gradle project at ${projectDir.path}", 0)
          context.message("Files found at the path: ${children.joinToString(prefix = "[", postfix = "]") { it.name }}", 0)
          return rejectedPromise()
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

        val promise = AsyncPromise<Void?>()
        ApplicationManager.getApplication().invokeLater {
          gradleSettings.linkProject(projectSettings)
          promise.setResult(null)
        }
        return promise
      }
      return resolvedPromise()
    }
  }
}
