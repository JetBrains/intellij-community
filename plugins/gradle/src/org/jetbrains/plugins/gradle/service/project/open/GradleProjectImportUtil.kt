// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleProjectImportUtil")
package org.jetbrains.plugins.gradle.service.project.open

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.setupGradleJvm
import java.nio.file.Path

fun canOpenGradleProject(file: VirtualFile): Boolean =
  GradleOpenProjectProvider().canOpenProject(file)

fun openGradleProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? =
  GradleOpenProjectProvider().openProject(projectFile, projectToClose, forceOpenInNewFrame)

@ApiStatus.Experimental
@JvmOverloads
fun canLinkAndRefreshGradleProject(projectFilePath: String, project: Project, showValidationDialog: Boolean = true): Boolean {
  val validationInfo = validateGradleProject(projectFilePath, project) ?: return true
  if (showValidationDialog) {
    val title = ExternalSystemBundle.message("error.project.import.error.title")
    when (validationInfo.warning) {
      true -> Messages.showWarningDialog(project, validationInfo.message, title)
      else -> Messages.showErrorDialog(project, validationInfo.message, title)
    }
  }
  return false
}

fun linkAndRefreshGradleProject(projectFilePath: String, project: Project) {
  GradleOpenProjectProvider().linkToExistingProject(projectFilePath, project)
}

@ApiStatus.Internal
fun createLinkSettings(projectDirectory: Path, project: Project): GradleProjectSettings {
  val gradleSettings = GradleSettings.getInstance(project)
  gradleSettings.setupGradleSettings()
  val gradleProjectSettings = GradleProjectSettings()
  gradleProjectSettings.setupGradleProjectSettings(project, projectDirectory)

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

@ApiStatus.Internal
fun GradleProjectSettings.setupGradleProjectSettings(project: Project, projectDirectory: Path) {
  externalProjectPath = projectDirectory.systemIndependentPath
  isUseQualifiedModuleNames = true
  distributionType = GradleEnvironment.Headless.GRADLE_DISTRIBUTION_TYPE?.let(DistributionType::valueOf)
                     ?: DistributionType.DEFAULT_WRAPPED
  gradleHome = GradleEnvironment.Headless.GRADLE_HOME ?: suggestGradleHome(project)
}

private fun suggestGradleHome(project: Project): String? {
  val installationManager = ApplicationManager.getApplication().getService(GradleInstallationManager::class.java)
  val lastUsedGradleHome = GradleUtil.getLastUsedGradleHome().nullize()
  if (lastUsedGradleHome != null) return lastUsedGradleHome
  val gradleHome = installationManager.getAutodetectedGradleHome(project) ?: return null
  return FileUtil.toCanonicalPath(gradleHome.path)
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