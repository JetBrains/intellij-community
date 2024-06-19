// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.workspace

import com.intellij.ide.workspace.ImportedProjectSettings
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.impl.workspace.ExternalSubproject
import com.intellij.platform.externalSystem.impl.workspace.ExternalSubprojectHandler
import com.intellij.util.PathUtil
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.project.open.canOpenGradleProject
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import javax.swing.Icon

internal class GradleSubprojectHandler : ExternalSubprojectHandler(GradleConstants.SYSTEM_ID) {

  override fun canImportFromFile(file: VirtualFile): Boolean {
    return canOpenGradleProject(file)
  }

  override fun importFromProject(project: Project): ImportedProjectSettings = GradleImportedProjectSettings(project)

  override fun suppressGenericImportFor(module: Module): Boolean {
    return ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId() == GradleConstants.SYSTEM_ID.id
  }

  override fun getSubprojects(workspace: Project): List<Subproject> {
    val imported = super.getSubprojects(workspace)
    val linkedProjectsSettings = GradleSettings.getInstance(workspace).linkedProjectsSettings
    val notImported = linkedProjectsSettings.mapNotNull { projectInfo ->
      if (imported.any { it.projectPath == projectInfo.externalProjectPath }) null else NotImported(projectInfo, this)
    }
    return imported + notImported
  }

  override fun removeSubprojects(workspace: Project, subprojects: List<Subproject>) {
    super.removeSubprojects(workspace, subprojects.filterIsInstance<ExternalSubproject>())
    val notImportedSubprojects = subprojects.filterIsInstance<NotImported>()
    if (notImportedSubprojects.isNotEmpty()) {
      notImportedSubprojects.forEach {
        GradleSettings.getInstance(workspace).unlinkExternalProject(it.projectPath)
      }
      ExternalSystemUtil.refreshProjects(ImportSpecBuilder(workspace, GradleConstants.SYSTEM_ID))
    }
  }

  override val subprojectIcon: Icon
    get() = GradleIcons.GradleSubproject

  private class NotImported(val projectInfo: GradleProjectSettings, override val handler: SubprojectHandler): Subproject {
    override val name: String
      get() = PathUtil.getFileName(projectPath)
    override val projectPath: String
      get() = projectInfo.externalProjectPath
  }
}

private class GradleImportedProjectSettings(project: Project): ImportedProjectSettings {
  private val gradleProjectsSettings: Collection<GradleProjectSettings> = GradleSettings.getInstance(project).linkedProjectsSettings
  private val projectDir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(project.basePath!!))

  override suspend fun applyTo(workspace: Project): Boolean {
    if (gradleProjectsSettings.isEmpty()) {
      if (canOpenGradleProject(projectDir) && isTrusted(projectDir, workspace)) {
        linkAndSyncGradleProject(workspace, projectDir)
        return true
      }
      return false
    }
    if (!isTrusted(projectDir, workspace)) {
      return true
    }
    val targetGradleSettings = GradleSettings.getInstance(workspace)
    val specBuilder = ImportSpecBuilder(workspace, GradleConstants.SYSTEM_ID)
      .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    for (setting in gradleProjectsSettings) {
      if (targetGradleSettings.getLinkedProjectSettings(setting.externalProjectPath) != null) {
        continue // already linked
      }
      targetGradleSettings.linkProject(setting)
      ExternalSystemUtil.refreshProject(setting.externalProjectPath, specBuilder)
    }
    return true
  }
}

private suspend fun isTrusted(projectDir: VirtualFile, project: Project) =
  ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProjectAsync(project, GradleConstants.SYSTEM_ID, projectDir.toNioPath())
