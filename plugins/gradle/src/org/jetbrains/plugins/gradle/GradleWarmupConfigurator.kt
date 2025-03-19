// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineProgressReporterElement
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.impl.ProjectOpenKeyProvider
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationManager
import org.jetbrains.plugins.gradle.service.project.GradleHeadlessLoggingProjectActivity.*
import org.jetbrains.plugins.gradle.service.project.isGradleProjectResolveTask
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.settings.GradleImportHintService
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.startup.GradleProjectSettingsUpdater
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext


private val LOG = logger<GradleCommandLineProjectConfigurator>()


private const val DISABLE_GRADLE_AUTO_IMPORT = "external.system.auto.import.disabled"
private const val DISABLE_GRADLE_JDK_FIX = "gradle.auto.auto.jdk.fix.disabled"
private const val DISABLE_ANDROID_GRADLE_PROJECT_STARTUP_ACTIVITY = "android.gradle.project.startup.activity.disabled"
private const val DISABLE_UPDATE_ANDROID_SDK_LOCAL_PROPERTIES = "android.sdk.local.properties.update.disabled"
private const val JDK_UPDATE_TIMEOUT_MINUTES = 10

class GradleWarmupConfigurator : WarmupConfigurator {

  override val configuratorPresentableName: String = "gradle"

  override suspend fun prepareEnvironment(projectPath: Path) {
    val reporter = coroutineContext[CommandLineProgressReporterElement.Key]
    prepareGradleConfiguratorEnvironment(reporter?.reporter)
  }

  override suspend fun runWarmup(project: Project): Boolean {
    val basePath = project.basePath ?: return false
    val service = service<EnvironmentService>()
    val projectSelectionKey = service.getEnvironmentValue(ProjectOpenKeyProvider.Keys.PROJECT_OPEN_PROCESSOR, "Gradle")
    if (projectSelectionKey != "Gradle") {
      // something else was selected to open the project
      return false
    }
    val state = GradleImportHintService.getInstance(project).state

    if (state.skip) return false
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      linkProjects(basePath, project)
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    val scope = CoroutineScope(coroutineContext)

    val notificationListener = StateNotificationListener(project, scope)
    val errorListener = ImportErrorListener(project, scope)

    val externalAnnotationsNotificationManager = ExternalAnnotationsProgressNotificationManager.getInstance()
    val externalAnnotationsProgressListener = StateExternalAnnotationNotificationListener()

    try {
      externalAnnotationsNotificationManager.addNotificationListener(externalAnnotationsProgressListener)
      progressManager.addNotificationListener(notificationListener)
      progressManager.addNotificationListener(errorListener)

      blockingContextScope {
        importProjects(project)
      }
      errorListener.error?.let { throw it }
    }
    finally {
      progressManager.removeNotificationListener(notificationListener)
      progressManager.removeNotificationListener(errorListener)

      externalAnnotationsNotificationManager.removeNotificationListener(externalAnnotationsProgressListener)
    }
    return false
  }

  private fun linkProjects(basePath: String, project: Project) {
    if (linkGradleHintProjects(basePath, project)) {
      return
    }
    linkRootProject(basePath, project)
  }

  private fun importProjects(project: Project) {
    val linkedProjectsSettings = GradleSettings.getInstance(project).linkedProjectsSettings
    if (linkedProjectsSettings.isEmpty()) return

    linkedProjectsSettings.forEach {
      GradleProjectSettingsUpdater.Util.updateGradleJvm(project, it).get(JDK_UPDATE_TIMEOUT_MINUTES.toLong(), TimeUnit.MINUTES)
    }

    AutoImportProjectTracker.onceIgnoreDisableAutoReloadRegistry()
    AutoImportProjectTracker.getInstance(project).scheduleProjectRefresh()
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






  class ImportErrorListener(
    private val project: Project, private val scope: CoroutineScope
  ) : ExternalSystemTaskNotificationListener {
    private val _error = AtomicReference<Throwable?>()
    val error get() = _error.get()

    private fun storeError(t: Throwable) {
      // thread safe version of if(error == null) error = t else error.addSuppressed(t)
      _error.compareAndExchange(null, t)?.addSuppressed(t)
    }

    override fun onSuccess(proojecPath: String, id: ExternalSystemTaskId) {
      if (!id.isGradleProjectResolveTask()) return

      project.messageBus.connect(scope)
        .subscribe(ProjectDataImportListener.TOPIC, object : ProjectDataImportListener {
          override fun onImportFailed(projectPath: String?, t: Throwable) {
            storeError(t)
          }
        })
    }

    override fun onFailure(proojecPath: String, id: ExternalSystemTaskId, exception: Exception) {
      if (!id.isGradleProjectResolveTask()) return
      storeError(exception)
    }
  }

}

internal fun prepareGradleConfiguratorEnvironment(logger: CommandLineInspectionProgressReporter?) {
  System.setProperty(DISABLE_GRADLE_AUTO_IMPORT, true.toString())
  System.setProperty(DISABLE_GRADLE_JDK_FIX, true.toString())
  System.setProperty(DISABLE_ANDROID_GRADLE_PROJECT_STARTUP_ACTIVITY, true.toString())
  System.setProperty(DISABLE_UPDATE_ANDROID_SDK_LOCAL_PROPERTIES, true.toString())
  val progressManager = ExternalSystemProgressNotificationManager.getInstance()
  if (logger != null) {
    progressManager.addNotificationListener(LoggingNotificationListener())
  }
  Unit
}


