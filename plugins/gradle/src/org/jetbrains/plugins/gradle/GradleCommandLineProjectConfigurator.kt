// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.settings.GradleImportHintService
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

private val LOG = Logger.getInstance(GradleCommandLineProjectConfigurator::class.java)

private val gradleLogWriter = BufferedWriter(FileWriter(PathManager.getLogPath() + "/gradle-import.log"))

private val GRADLE_OUTPUT_LOG = Logger.getInstance("GradleOutput")

private const val DISABLE_GRADLE_AUTO_IMPORT = "external.system.auto.import.disabled"

class GradleCommandLineProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName() = "gradle"

  override fun getDescription(): String = GradleBundle.message("gradle.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get(DISABLE_GRADLE_AUTO_IMPORT).setValue(true)
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(LoggingNotificationListener())
    Unit
  }

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    val basePath = project.basePath ?: return
    val state = GradleImportHintService.getInstance(project).state

    if (state.skip) return
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      linkProjects(basePath, project)
    }

    val externalSystemState = ConcurrentHashMap<ExternalSystemTaskId, ExternalSystemState>()
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(StateNotificationListener(externalSystemState))

    importProjects(project)

    checkImportState(externalSystemState)
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

  private fun checkImportState(externalSystemState: Map<ExternalSystemTaskId, ExternalSystemState>) {
    externalSystemState.forEach { (key, value) ->
      if (value != ExternalSystemState.SUCCESS) {
        throw IllegalStateException("Gradle project ${key.ideProjectId} import failed. Project import status: $value")
      }
    }
  }

  class StateNotificationListener(private val externalSystemState: MutableMap<ExternalSystemTaskId, ExternalSystemState>) :
    ExternalSystemTaskNotificationListenerAdapter() {
    override fun onSuccess(id: ExternalSystemTaskId) {
      externalSystemState[id] = ExternalSystemState.SUCCESS
      LOG.info("Gradle import success: ${id.ideProjectId}")
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      externalSystemState[id] = ExternalSystemState.FAILURE
      LOG.error("Gradle import failure ${id.ideProjectId}", e)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
      externalSystemState[id] = ExternalSystemState.CANCELLED
      LOG.error("Gradle import canceled ${id.ideProjectId}")
    }

    override fun onStart(id: ExternalSystemTaskId) {
      externalSystemState[id] = ExternalSystemState.STARTED
      LOG.info("Gradle import started ${id.ideProjectId}")
    }
  }

  class LoggingNotificationListener() : ExternalSystemTaskNotificationListenerAdapter() {
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      val gradleText = (if (stdOut) "" else "STDERR: ") + text
      gradleLogWriter.write(gradleText)
      GRADLE_OUTPUT_LOG.debug(gradleText)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
      gradleLogWriter.flush()
    }
  }

  enum class ExternalSystemState {
    STARTED,
    CANCELLED,
    FAILURE,
    SUCCESS
  }
}
