// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.plugins.gradle.service.project.GradleNotification
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.exists

internal object GradleDependencySourceDownloaderErrorHandler {
  private const val BUILD_GRADLE = "build.gradle"
  private const val BUILD_GRADLE_KTS = "$BUILD_GRADLE.kts"

  private const val DEFAULT_FAILURE_NOTIFICATION_ID = "gradle.notifications.sources.download.failed"
  private const val ARTIFACT_NOT_FOUND_IN_REPOSITORY_NOTIFICATION_ID = "gradle.notifications.sources.download.from.repository.failed"
  private val artifactRepositoryRegexPattern = """
      Searched in the following locations:
      \s.(.+)
    """.trimIndent()
  private val artifactRepositoryRegex = Pattern.compile(artifactRepositoryRegexPattern)

  fun handle(project: Project, externalProjectPath: String, artifact: String, exception: Exception) {
    val repository = getUsedRepository(exception)
    if (repository != null) {
      val projectRepository = findProjectRepository(project, repository)
      if (projectRepository != null) {
        showNotificationForRepository(project, externalProjectPath, artifact, projectRepository)
        return
      }
    }
    showDefaultNotification(project, artifact)
  }

  private fun showDefaultNotification(project: Project, artifact: String) {
    GradleNotification.gradleNotificationGroup
      .createNotification(title = GradleBundle.message("gradle.notifications.sources.download.failed.title"),
                          content = GradleBundle.message("gradle.notifications.sources.download.failed.content", artifact),
                          NotificationType.WARNING)
      .setDisplayId(DEFAULT_FAILURE_NOTIFICATION_ID)
      .notify(project)
  }

  private fun showNotificationForRepository(project: Project,
                                            externalProjectPath: String,
                                            artifact: String,
                                            repository: RemoteRepositoryDescription) {
    val actions = mutableListOf<AnAction>()
    actions.addIfNotNull(getAction(project, externalProjectPath))
    GradleNotification.gradleNotificationGroup
      .createNotification(
        title = GradleBundle.message("gradle.notifications.sources.download.failed.title"),
        content = GradleBundle.message("gradle.notifications.sources.download.from.repository.failed.content", artifact, repository.name),
        NotificationType.WARNING
      )
      .addActions(actions as Collection<AnAction>)
      .setDisplayId(ARTIFACT_NOT_FOUND_IN_REPOSITORY_NOTIFICATION_ID)
      .notify(project)
  }

  private fun getAction(project: Project, externalProjectPath: String): AnAction? {
    val projectRoot = externalProjectPath.toNioPathOrNull() ?: return null
    val buildScriptPath: Path = getFilePathIfFileExist(projectRoot, BUILD_GRADLE)
                                ?: getFilePathIfFileExist(projectRoot, BUILD_GRADLE_KTS)
                                ?: return null
    val buildScriptVirtualFile = buildScriptPath.refreshAndFindVirtualFileOrDirectory() ?: return null
    return NotificationAction.createSimple(GradleBundle.message("gradle.notifications.sources.download.action.title", buildScriptVirtualFile.name)) {
      if (buildScriptVirtualFile.isValid) {
        FileEditorManager.getInstance(project).openFile(buildScriptVirtualFile)
      }
    }
  }

  private fun getFilePathIfFileExist(root: Path, fileName: String): Path? {
    val path = root.resolve(fileName)
    if (path.exists()) {
      return path
    }
    return null
  }

  private fun getUsedRepository(exception: Throwable): String? {
    val matcher = artifactRepositoryRegex.matcher(exception.message!!)
    if (!matcher.find()) {
      return null
    }
    return matcher.group(1).trim()
  }

  private fun findProjectRepository(project: Project, usedRepository: String): RemoteRepositoryDescription? {
    val projectRepositories = RemoteRepositoriesConfiguration.getInstance(project)
    return projectRepositories.repositories.find { usedRepository.startsWith(it.url) }
  }
}