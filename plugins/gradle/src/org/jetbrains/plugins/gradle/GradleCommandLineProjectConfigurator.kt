// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationListener
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationManager
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsTaskId
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.settings.GradleImportHintService
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private val LOG = Logger.getInstance(GradleCommandLineProjectConfigurator::class.java)

private val gradleLogWriter = BufferedWriter(FileWriter(PathManager.getLogPath() + "/gradle-import.log"))

private val GRADLE_OUTPUT_LOG = Logger.getInstance("GradleOutput")

private const val DISABLE_GRADLE_AUTO_IMPORT = "external.system.auto.import.disabled"
private const val DISABLE_ANDROID_GRADLE_PROJECT_STARTUP_ACTIVITY = "android.gradle.project.startup.activity.disabled"
private const val DISABLE_UPDATE_ANDROID_SDK_LOCAL_PROPERTIES = "android.sdk.local.properties.update.disabled"

class GradleCommandLineProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName() = "gradle"

  override fun getDescription(): String = GradleBundle.message("gradle.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get(DISABLE_GRADLE_AUTO_IMPORT).setValue(true)
    Registry.get(DISABLE_ANDROID_GRADLE_PROJECT_STARTUP_ACTIVITY).setValue(true)
    Registry.get(DISABLE_UPDATE_ANDROID_SDK_LOCAL_PROPERTIES).setValue(true)
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(LoggingNotificationListener(context.logger))
    Unit
  }

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    val basePath = project.basePath ?: return
    val state = GradleImportHintService.getInstance(project).state

    if (state.skip) return
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      linkProjects(basePath, project)
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    val notificationListener = StateNotificationListener(project)

    val externalAnnotationsNotificationManager = ExternalAnnotationsProgressNotificationManager.getInstance()
    val externalAnnotationsProgressListener = StateExternalAnnotationNotificationListener()

    try {
      externalAnnotationsNotificationManager.addNotificationListener(externalAnnotationsProgressListener)
      progressManager.addNotificationListener(notificationListener)
      importProjects(project)
      notificationListener.waitForImportEnd()
      externalAnnotationsProgressListener.waitForResolveExternalAnnotationEnd()
    }
    finally {
      progressManager.removeNotificationListener(notificationListener)
      externalAnnotationsNotificationManager.removeNotificationListener(externalAnnotationsProgressListener)
    }
  }

  private fun linkProjects(basePath: String, project: Project) {
    if (linkGradleHintProjects(basePath, project)) {
      return
    }
    linkRootProject(basePath, project)
  }

  private fun importProjects(project: Project) {
    if (!GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      Registry.get(DISABLE_GRADLE_AUTO_IMPORT).setValue(false)
      AutoImportProjectTracker.getInstance(project).scheduleProjectRefresh()
      Registry.get(DISABLE_GRADLE_AUTO_IMPORT).setValue(true)
    }
  }

  private fun linkRootProject(basePath: String, project: Project): Boolean {
    val gradleGroovyDslFile = basePath + "/" + GradleConstants.DEFAULT_SCRIPT_NAME
    val kotlinDslGradleFile = basePath + "/" + GradleConstants.KOTLIN_DSL_SCRIPT_NAME
    if (FileUtil.findFirstThatExist(gradleGroovyDslFile, kotlinDslGradleFile) == null) return false

    LOG.info("Link gradle project from root directory")

    val settings = createLinkSettings(Paths.get(basePath), project)
    ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkProject(settings)
    return true
  }

  private fun linkGradleHintProjects(basePath: String, project: Project): Boolean {
    val state = GradleImportHintService.getInstance(project).state

    if (state.projectsToImport.isNotEmpty()) {
      for (projectPath in state.projectsToImport) {
        val buildFile = File(basePath).toPath().resolve(projectPath)
        if (buildFile.toFile().exists()) {
          LOG.info("Link gradle project from intellij.yaml: $projectPath")

          val settings = createLinkSettings(buildFile.parent, project)
          ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).linkProject(settings)
        }
        else {
          LOG.error("File for linking gradle project doesn't exist: " + buildFile.toAbsolutePath().toString())
          return false
        }
      }
      return true
    }
    return false
  }

  private class StateExternalAnnotationNotificationListener : ExternalAnnotationsProgressNotificationListener {
    private val externalAnnotationsState = ConcurrentHashMap<ExternalAnnotationsTaskId, CompletableFuture<ExternalAnnotationsTaskId>>()

    override fun onStartResolve(id: ExternalAnnotationsTaskId) {
      externalAnnotationsState[id] = CompletableFuture()
      LOG.info("Gradle resolving external annotations started ${id.projectId}")
    }

    override fun onFinishResolve(id: ExternalAnnotationsTaskId) {
      val feature = externalAnnotationsState[id] ?: return
      feature.complete(id)
      LOG.info("Gradle resolving external annotations completed ${id.projectId}")
    }

    fun waitForResolveExternalAnnotationEnd() {
      externalAnnotationsState.values.forEach { it.get() }
    }
  }

  class StateNotificationListener(private val project: Project) : ExternalSystemTaskNotificationListenerAdapter() {
    private val externalSystemState = ConcurrentHashMap<ExternalSystemTaskId, CompletableFuture<ExternalSystemTaskId>>()

    override fun onSuccess(id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      LOG.info("Gradle resolve success: ${id.ideProjectId}")
      val connection = project.messageBus.simpleConnect()
      connection.subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
        override fun onImportFinished(projectPath: String?) {
          LOG.info("Gradle import success: ${id.ideProjectId}")
          val future = externalSystemState[id] ?: return
          future.complete(id)
        }

        override fun onImportFailed(projectPath: String?) {
          LOG.info("Gradle import failure: ${id.ideProjectId}")
          val future = externalSystemState[id] ?: return
          future.completeExceptionally(IllegalStateException("Gradle project ${id.ideProjectId} import failed."))
        }
      })
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      if (!id.isGradleProjectResolveTask()) return
      LOG.error("Gradle resolve failure ${id.ideProjectId}", e)
      val future = externalSystemState[id] ?: return
      future.completeExceptionally(IllegalStateException("Gradle project ${id.ideProjectId} resolve failed.", e))
    }

    override fun onCancel(id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      LOG.error("Gradle resolve canceled ${id.ideProjectId}")
      val future = externalSystemState[id] ?: return
      future.completeExceptionally(IllegalStateException("Resolve of ${id.ideProjectId} was canceled"))
    }

    override fun onStart(id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return
      externalSystemState[id] = CompletableFuture()
      LOG.info("Gradle project resolve started ${id.ideProjectId}")
    }

    fun waitForImportEnd() {
      externalSystemState.values.forEach { it.get() }
    }

    private fun ExternalSystemTaskId.isGradleProjectResolveTask() = this.projectSystemId == GradleConstants.SYSTEM_ID &&
                                                                    this.type == ExternalSystemTaskType.RESOLVE_PROJECT
  }

  class LoggingNotificationListener(val logger: CommandLineInspectionProgressReporter) : ExternalSystemTaskNotificationListenerAdapter() {
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      val gradleText = (if (stdOut) "" else "STDERR: ") + text
      gradleLogWriter.write(gradleText)
      logger.reportMessage(1, gradleText)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
      gradleLogWriter.flush()
    }
  }
}
