// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.exists
import org.jetbrains.plugins.gradle.settings.GradleImportHintService
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(GradleCommandLineProjectConfigurator::class.java)

private val gradleLogWriter = BufferedWriter(FileWriter(PathManager.getLogPath() + "/gradle-import.log"))

class GradleCommandLineProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName() = "gradle"

  override fun getDescription(): String = GradleBundle.message("gradle.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get("external.system.auto.import.disabled").setValue(true)
  }

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    val basePath = project.basePath ?: return
    val state = GradleImportHintService.getInstance(project).state

    if (state.skip) return
    val finishStatus = AtomicReference(FinishStatus.INITIAL)
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(TaskNotificationListener(finishStatus))

    if (state.projectsToImport.isNotEmpty()) {
      for (projectPath in state.projectsToImport) {
        val buildFile = File(basePath).toPath().resolve(projectPath).toFile()
        if (buildFile.exists()) {
          refreshProject(buildFile.absolutePath, getImportSpecBuilder(project))
        }
        else {
          LOG.warn("File for importing gradle project doesn't exist: " + buildFile.absolutePath)
        }
      }
      return
    }

    val wasAlreadyImported = context.projectPath.resolve(".idea").exists()
    if (wasAlreadyImported && !GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      refreshProjects(getImportSpecBuilder(project))
      return
    }

    val gradleGroovyDslFile = basePath + "/" + GradleConstants.DEFAULT_SCRIPT_NAME
    val kotlinDslGradleFile = basePath + "/" + GradleConstants.KOTLIN_DSL_SCRIPT_NAME
    if (FileUtil.findFirstThatExist(gradleGroovyDslFile, kotlinDslGradleFile) == null) return

    refreshProject(basePath, getImportSpecBuilder(project))
    val status = finishStatus.get()
    if (status != FinishStatus.SUCCESS && status != FinishStatus.INITIAL) {
      throw IllegalStateException("Gradle project import failure. Project import status: $status")
    }
  }

  private fun getImportSpecBuilder(project: Project): ImportSpecBuilder =
    ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(MODAL_SYNC)

  class TaskNotificationListener(private val finishStatus: AtomicReference<FinishStatus>) : ExternalSystemTaskNotificationListener {
    override fun onSuccess(id: ExternalSystemTaskId) {
      finishStatus.set(FinishStatus.SUCCESS)
      gradleLogWriter.close()
      LOG.info("Gradle import success")
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      finishStatus.set(FinishStatus.FAILURE)
      gradleLogWriter.close()
      LOG.error("Gradle import failure", e)
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      gradleLogWriter.write((if (stdOut) "" else "STDERR: ") + text)
    }

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
    }

    override fun onCancel(id: ExternalSystemTaskId) {
      finishStatus.set(FinishStatus.CANCELLED)
      LOG.error("Gradle import canceled")
    }

    override fun onEnd(id: ExternalSystemTaskId) {
      gradleLogWriter.close()
    }

    override fun beforeCancel(id: ExternalSystemTaskId) {
    }

    override fun onStart(id: ExternalSystemTaskId) {
      finishStatus.set(FinishStatus.STARTED)
      LOG.info("Gradle import started")
    }
  }

  enum class FinishStatus {
    INITIAL,
    STARTED,
    CANCELLED,
    FAILURE,
    SUCCESS
  }
}
