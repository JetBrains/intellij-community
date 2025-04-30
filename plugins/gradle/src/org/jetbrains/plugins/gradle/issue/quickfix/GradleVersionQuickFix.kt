// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.SyncViewManager
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.NO_PROGRESS_ASYNC
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperConfiguration
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix.Companion.showWrapperPropertiesFile
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.createFile

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
class GradleVersionQuickFix(
  private val projectPath: String,
  private val gradleVersion: GradleVersion,
  private val requestImport: Boolean,
) : BuildIssueQuickFix {

  override val id: String = "fix_gradle_version_in_wrapper"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    return project.gradleCoroutineScope.launch {
      runBatchChange(project) {
        updateOrCreateWrapper(project)
        showWrapperPropertiesFile(project)
        resetProjectDistributionType(project)
        runWrapperTask(project)
        if (requestImport) {
          delay(500) // todo remove when multiple-build view will be integrated into the BuildTreeConsoleView
          val importFuture = ExternalSystemUtil.requestImport(project, projectPath, GradleConstants.SYSTEM_ID)
          importFuture.await()
        }
      }
    }.asCompletableFuture()
  }

  private fun resetProjectDistributionType(project: Project) {
    val gradleSettings = GradleSettings.getInstance(project)
    val linkedProjectSettings = gradleSettings.getLinkedProjectSettings(projectPath)!!
    linkedProjectSettings.distributionType = DistributionType.DEFAULT_WRAPPED
  }

  private suspend fun updateOrCreateWrapper(project: Project) {
    try {
      updateOrCreateWrapper()
    }
    catch (e: IOException) {
      LOG.warn(e)
      showUnableToCreateWrapperNotification(project)
      throw e
    }
  }

  private fun showUnableToCreateWrapperNotification(project: Project) {
    val title = GradleBundle.message("gradle.version.quick.fix.error")
    val message = GradleBundle.message("gradle.version.quick.fix.error.description", ShowLogAction.getActionName())
    val notification = NotificationData(title, message, WARNING, PROJECT_SYNC)
      .apply {
        isBalloonNotification = true
        balloonGroup = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Wrapper Update")
        setListener("#open_log") { _, _ -> ShowLogAction.showLog() }
      }
    ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification)
  }

  private suspend fun showWrapperPropertiesFile(project: Project) {
    withContext(Dispatchers.IO) {
      showWrapperPropertiesFile(project, projectPath, gradleVersion.version)
    }
  }

  /**
   * This is only a fallback, because immediately after updating `gradle-wrapper.properties` the `wrapper` task will be executed explicitly.
   * The fallback is necessary because if the `wrapper` task fails for some unknown reason, the next interaction with Gradle will download
   * the correct Gradle version.
   */
  private suspend fun updateOrCreateWrapper() {
    withContext(Dispatchers.IO) {
      var wrapperPropertiesPath = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
      val wrapperConfiguration = getWrapperConfiguration(wrapperPropertiesPath, gradleVersion)

      if (wrapperPropertiesPath == null) {
        wrapperPropertiesPath = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.properties")
        wrapperPropertiesPath.createParentDirectories().createFile()
      }

      GradleUtil.writeWrapperConfiguration(wrapperConfiguration, wrapperPropertiesPath)
      LocalFileSystem.getInstance().refreshNioFiles(listOf(wrapperPropertiesPath))
    }
  }

  /**
   * The recommended way to update the Gradle version. There are several reasons to use the `wrapper` task:
   * 1) The wrapper task results in recreation for all Gradle wrapper related files such as `gradlew.bat`, `gradlew` and `gradle-wrapper.jar`,
   *  so, CLI can use the updated wrappers around the Gradle wrapper.
   * 2) If the user has explicitly configured the wrapper task in the `build.gradle` file, a quick fix will install the version that was
   *  required by user.
   */
  private suspend fun runWrapperTask(project: Project) {
    val userData = UserDataHolderBase()
    val initScript = "gradle.projectsEvaluated { g ->\n" +
                     "  def wrapper = g.rootProject.tasks.wrapper\n" +
                     "  if (wrapper == null) return \n" +
                     "  wrapper.gradleVersion = '" + gradleVersion.version + "'\n" +
                     "}\n"
    userData.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
    userData.putUserData(PROGRESS_LISTENER_KEY, SyncViewManager::class.java)

    val gradleVmOptions = GradleSettings.getInstance(project).gradleVmOptions
    val settings = ExternalSystemTaskExecutionSettings()
    settings.executionName = GradleBundle.message("grable.execution.name.upgrade.wrapper")
    settings.externalProjectPath = projectPath
    settings.taskNames = listOf("wrapper")
    settings.vmOptions = gradleVmOptions
    settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id

    val future = CompletableFuture<Boolean>()
    val task = TaskExecutionSpec.create()
      .withProject(project)
      .withSystemId(GradleConstants.SYSTEM_ID)
      .withSettings(settings)
      .withActivateToolWindowBeforeRun(false)
      .withProgressExecutionMode(NO_PROGRESS_ASYNC)
      .withUserData(userData)
      .withCallback(future)
      .build()
    runTask(task)

    if (!future.await()) {
      throw RuntimeException("Wrapper task failed")
    }
  }

  private fun getWrapperConfiguration(wrapperPropertiesPath: Path?, gradleVersion: GradleVersion): WrapperConfiguration {
    val configuration = wrapperPropertiesPath?.let { GradleUtil.readWrapperConfiguration(it) }
    if (configuration == null) {
      return GradleUtil.generateGradleWrapperConfiguration(gradleVersion)
    }
    configuration.distribution = getWrapperDistributionUri(gradleVersion)
    return configuration
  }

  // Auto-import and indexing should be disabled while Gradle wrapper is in an incorrect state and cannot be used
  private suspend fun <T> runBatchChange(project: Project, execution: suspend () -> T): T {
    val publisher = BackgroundTaskUtil.syncPublisher(BatchFileChangeListener.TOPIC)
    publisher.batchChangeStarted(project, GradleBundle.message("grable.execution.name.upgrade.wrapper"))
    try {
      return execution.invoke()
    }
    finally {
      publisher.batchChangeCompleted(project)
    }
  }

  companion object {
    private val LOG = logger<GradleVersionQuickFix>()
  }
}
