// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.gradle.properties.GradleVersionQuickFix.Companion.VERSION_SPECIFIC_WRAPPER_KEYS
import com.intellij.ide.actions.ShowLogAction
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemAutoImportAwareListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix.Companion.showWrapperPropertiesFile
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds

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
        if (!updateWrapper(project)) return@runBatchChange
        showWrapperPropertiesFile(project)
        if (requestImport) {
          delay(500.milliseconds) // todo remove when multiple-build view will be integrated into the BuildTreeConsoleView
          val importFuture = ExternalSystemUtil.requestImport(project, projectPath, GradleConstants.SYSTEM_ID)
          importFuture.await()
        }
      }
    }.asCompletableFuture()
  }

  private suspend fun updateWrapper(project: Project): Boolean {
    try {
      val path = GradleUtil.findDefaultWrapperPropertiesFile(Path.of(projectPath)) ?: return false
      return updateWrapper(project, path)
    }
    catch (e: IOException) {
      LOG.warn(e)
      showUnableToUpdateWrapperNotification(project)
      throw e
    }
  }

  private fun showUnableToUpdateWrapperNotification(project: Project) {
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
      showWrapperPropertiesFile(project, Path.of(projectPath), gradleVersion.version)
    }
  }

  /**
   * Update the gradle-wrapper.properties, changing the distributionUrl to the new version
   * and dropping version-specific keys ([VERSION_SPECIFIC_WRAPPER_KEYS]).
   */
  private suspend fun updateWrapper(project: Project, wrapperPropertiesPath: Path): Boolean {
    val virtualFile = wrapperPropertiesPath.refreshAndFindVirtualFile() ?: return false
    val propertiesFile = readAction {
      PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile
    } ?: return false
    writeCommandAction(project, GradleBundle.message("gradle.version.quick.fix.editor.command.name")) {
      updateGradleWrapperVersion(propertiesFile, gradleVersion)
    }
    return true
  }

  // Auto-import and indexing should be disabled while Gradle wrapper is in an incorrect state and cannot be used
  private suspend fun <T> runBatchChange(project: Project, execution: suspend () -> T): T {
    // BatchFileChangeListener.TOPIC should be used there, but it was substituted to ExternalSystemAutoImportAwareListener.TOPIC only
    // to disable auto-sync. Indexing could happen during the execution.
    // The original topic should be returned as a result of IDEA-389819.
    val publisher = BackgroundTaskUtil.syncPublisher(project, ExternalSystemAutoImportAwareListener.TOPIC)
    publisher.autoImportAwareOperationStarted()
    try {
      return execution.invoke()
    }
    finally {
      publisher.autoImportAwareOperationCompleted()
    }
  }

  companion object {
    private val LOG = logger<GradleVersionQuickFix>()
    private const val DISTRIBUTION_URL_KEY = "distributionUrl"
    private val VERSION_SPECIFIC_WRAPPER_KEYS = setOf("distributionSha256Sum")
    val DISTRIBUTION_URL_VERSION_REGEX: Regex = Regex("""(.*gradle-)(.+?)(-(?:bin|all)\.zip)""")

    /**
     * Updates the Gradle wrapper [propertiesFile] to [newVersion]: replaces the version segment of the
     * `distributionUrl` value (adding the property if it is missing), and drops version-specific keys
     * ([VERSION_SPECIFIC_WRAPPER_KEYS]) that no longer match the new distribution.
     */
    fun updateGradleWrapperVersion(propertiesFile: PropertiesFile, newVersion: GradleVersion) {
      val distributionUrlProperty = propertiesFile.findPropertyByKey(DISTRIBUTION_URL_KEY)
      if (distributionUrlProperty == null) {
        propertiesFile.addProperty(DISTRIBUTION_URL_KEY, getWrapperDistributionUri(newVersion).toString(), PropertyKeyValueFormat.FILE)
      }
      else {
        val bumpedUrl = distributionUrlProperty.value?.let { replaceDistributionUrlVersion(it, newVersion) }
        if (bumpedUrl != null) {
          distributionUrlProperty.setValue(bumpedUrl, PropertyKeyValueFormat.FILE)
        }
      }
      for (key in VERSION_SPECIFIC_WRAPPER_KEYS) {
        propertiesFile.findPropertyByKey(key)?.psiElement?.delete()
      }
    }

    /**
     * Replaces only the Gradle version segment of a wrapper `distributionUrl`, preserving the host and the `bin`/`all`
     * distribution variant. For example `https://mirror/dists/gradle-8.5-all.zip` with the new version `8.9` becomes
     * `https://mirror/dists/gradle-8.9-all.zip`.
     *
     * @return the URL with the version replaced, or `null` if it has no parseable `gradle-<version>-(bin|all).zip` segment
     * (in which case the caller should keep the original URL untouched).
     */
    fun replaceDistributionUrlVersion(distributionUrl: String, newVersion: GradleVersion): String? {
      val match = DISTRIBUTION_URL_VERSION_REGEX.matchEntire(distributionUrl) ?: return null
      return match.groupValues[1] + newVersion.version + match.groupValues[3]
    }
  }
}
