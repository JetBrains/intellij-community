// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.SyncViewManager
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix.Companion.requestImport
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.NO_PROGRESS_ASYNC
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.runTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.outputStream
import org.gradle.internal.impldep.com.google.common.base.Charsets
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix.Companion.showWrapperPropertiesFile
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.OutputStreamWriter
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import kotlin.io.path.createFile
import kotlin.io.path.inputStream

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
class GradleVersionQuickFix(private val projectPath: String,
                            private val gradleVersion: GradleVersion,
                            private val requestImport: Boolean) : BuildIssueQuickFix {

  override val id: String = "fix_gradle_version_in_wrapper"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    return updateOrCreateWrapper()
      .exceptionally {
        LOG.warn(it)
        val title = GradleBundle.message("gradle.version.quick.fix.error")
        val message = GradleBundle.message("gradle.version.quick.fix.error.description", ShowLogAction.getActionName())
        val notification = NotificationData(title, message, WARNING, PROJECT_SYNC)
          .apply {
            isBalloonNotification = true
            balloonGroup = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Wrapper Update")
            setListener("#open_log") { _, _ -> ShowLogAction.showLog() }
          }
        ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification)
        throw it
      }
      .thenApply {
        GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath)!!.distributionType = DistributionType.DEFAULT_WRAPPED
      }
      .thenComposeAsync { runWrapperTask(project) }
      .thenApply { showWrapperPropertiesFile(project, projectPath, gradleVersion.version) }
      .thenComposeAsync {
        when {
          requestImport -> {
            TimeoutUtil.sleep(500) // todo remove when multiple-build view will be integrated into the BuildTreeConsoleView
            return@thenComposeAsync requestImport(project, projectPath, GradleConstants.SYSTEM_ID)
          }
          else -> return@thenComposeAsync completedFuture(null)
        }
      }
  }

  private fun updateOrCreateWrapper(): CompletableFuture<*> {
    return CompletableFuture.supplyAsync {
      val wrapperProperties: Properties
      var wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
      val distributionUrl = "https://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      if (wrapperPropertiesFile == null) {
        val wrapperPropertiesPath = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.properties")
        wrapperPropertiesPath.createParentDirectories().createFile()
        wrapperPropertiesFile = wrapperPropertiesPath
        wrapperProperties = getDefaultGradleProperties(distributionUrl)
      }
      else {
        wrapperProperties = wrapperPropertiesFile.inputStream().use { stream ->
          Properties().also { it.load(stream) }
        }
        wrapperProperties[WrapperExecutor.DISTRIBUTION_URL_PROPERTY] = distributionUrl
      }

      wrapperPropertiesFile?.outputStream()?.use { out ->
        OutputStreamWriter(out, Charsets.ISO_8859_1).use { writer ->
          wrapperProperties.store(writer, null)
        }
      }
      LocalFileSystem.getInstance().refreshNioFiles(listOf(wrapperPropertiesFile))
    }
  }

  private fun runWrapperTask(project: Project): CompletableFuture<Nothing> {
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

    val future = CompletableFuture<Nothing>()
    runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            object : TaskCallback {
              override fun onSuccess() {
                future.complete(null)
              }

              override fun onFailure() {
                future.completeExceptionally(RuntimeException("Wrapper task failed"))
              }
            }, NO_PROGRESS_ASYNC, false, userData)
    return future
  }

  private fun getDefaultGradleProperties(distributionUrl: String): Properties = Properties().apply {
    this[WrapperExecutor.DISTRIBUTION_URL_PROPERTY] = distributionUrl
    this[WrapperExecutor.DISTRIBUTION_BASE_PROPERTY] = "GRADLE_USER_HOME"
    this[WrapperExecutor.DISTRIBUTION_PATH_PROPERTY] = "wrapper/dists"
    this[WrapperExecutor.ZIP_STORE_BASE_PROPERTY] = "GRADLE_USER_HOME"
    this[WrapperExecutor.ZIP_STORE_PATH_PROPERTY] = "wrapper/dists"
  }

  companion object {
    private val LOG = logger<GradleVersionQuickFix>()
  }
}
