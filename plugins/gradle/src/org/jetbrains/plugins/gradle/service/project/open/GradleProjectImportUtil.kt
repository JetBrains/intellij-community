// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleProjectImportUtil")
package org.jetbrains.plugins.gradle.service.project.open

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.setupGradleJvm
import java.nio.file.Path

fun canOpenGradleProject(file: VirtualFile): Boolean = GradleOpenProjectProvider().canOpenProject(file)

suspend fun openGradleProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
  return GradleOpenProjectProvider().openProject(projectFile, projectToClose, forceOpenInNewFrame)
}

@ApiStatus.Experimental
@JvmOverloads
fun canLinkAndRefreshGradleProject(projectFilePath: String, project: Project, showValidationDialog: Boolean = true): Boolean {
  val validationInfo = validateGradleProject(projectFilePath, project) ?: return true
  if (showValidationDialog) {
    val title = ExternalSystemBundle.message("error.project.import.error.title")
    invokeAndWaitIfNeeded {
      when (validationInfo.warning) {
        true -> Messages.showWarningDialog(project, validationInfo.message, title)
        else -> Messages.showErrorDialog(project, validationInfo.message, title)
      }
    }
  }
  return false
}

@Deprecated("Use linkAndSyncGradleProject instead", ReplaceWith("linkAndSyncGradleProject(project, projectFilePath)"))
fun linkAndRefreshGradleProject(projectFilePath: String, project: Project) {
  LOG.warn("Use linkAndSyncGradleProject instead")
  CoroutineScopeService.getCoroutineScope(project).launchTracked {
    GradleOpenProjectProvider().linkToExistingProjectAsync(projectFilePath, project)
  }
}

suspend fun linkAndSyncGradleProject(project: Project, projectFilePath: String) {
  GradleOpenProjectProvider().linkToExistingProjectAsync(projectFilePath, project)
}

suspend fun linkAndSyncGradleProject(project: Project, projectFile: VirtualFile) {
  GradleOpenProjectProvider().linkToExistingProjectAsync(projectFile, project)
}

@ApiStatus.Internal
fun createLinkSettings(projectDirectory: Path, project: Project): GradleProjectSettings {
  val gradleSettings = GradleSettings.getInstance(project)
  gradleSettings.setupGradleSettings()
  val gradleProjectSettings = GradleDefaultProjectSettings.createProjectSettings(projectDirectory.toCanonicalPath())

  val gradleVersion = gradleProjectSettings.resolveGradleVersion()
  setupGradleJvm(project, gradleProjectSettings, gradleVersion)
  return gradleProjectSettings
}

@ApiStatus.Internal
fun GradleSettings.setupGradleSettings() {
  gradleVmOptions = GradleEnvironment.Headless.GRADLE_VM_OPTIONS ?: gradleVmOptions
  isOfflineWork = GradleEnvironment.Headless.GRADLE_OFFLINE?.toBoolean() ?: isOfflineWork
  serviceDirectoryPath = GradleEnvironment.Headless.GRADLE_SERVICE_DIRECTORY ?: serviceDirectoryPath
  storeProjectFilesExternally = true
}

fun suggestGradleHome(project: Project?): String? {
  val defaultGradleHome = GradleDefaultProjectSettings.getInstance().gradleHome
  if (defaultGradleHome != null) {
    return defaultGradleHome
  }
  val lastUsedGradleHome = GradleUtil.getLastUsedGradleHome()
  if (lastUsedGradleHome.isNotEmpty()) {
    return lastUsedGradleHome
  }
  val gradleHome = GradleInstallationManager.getInstance().getAutodetectedGradleHome(project)
  return gradleHome?.toPath()?.toCanonicalPath()
}

private fun validateGradleProject(projectFilePath: String, project: Project): ValidationInfo? {
  val systemSettings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)
  val localFileSystem = LocalFileSystem.getInstance()
  val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
  if (projectFile == null) {
    val shortPath = getPresentablePath(projectFilePath)
    return ValidationInfo(ExternalSystemBundle.message("error.project.does.not.exist", "Gradle", shortPath))
  }
  val projectDirectory = if (projectFile.isDirectory) projectFile else projectFile.parent
  val projectSettings = systemSettings.getLinkedProjectSettings(projectDirectory.path)
  if (projectSettings != null) return ValidationInfo(ExternalSystemBundle.message("error.project.already.registered"))
  return null
}

@Service(Service.Level.PROJECT)
private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
  companion object {
    fun getCoroutineScope(project: Project): CoroutineScope {
      return project.service<CoroutineScopeService>().coroutineScope
    }
  }
}

private val LOG = Logger.getInstance("#org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil")