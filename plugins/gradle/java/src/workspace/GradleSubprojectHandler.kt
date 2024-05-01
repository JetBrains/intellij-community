// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.workspace

import com.intellij.ide.workspace.ImportedProjectSettings
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.service.project.open.canLinkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.service.project.open.canOpenGradleProject
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleSubprojectHandler : SubprojectHandler {
  override fun getSubprojects(project: Project): List<Subproject> {
    val gradleProjects = GradleSettings.getInstance(project).linkedProjectsSettings
    return gradleProjects.map { gradleProject -> GradleSubproject(project, gradleProject) }
  }

  override fun getSubprojectFor(module: Module): Subproject? {
    val project = module.project
    val moduleManager = ExternalSystemModulePropertyManager.getInstance(module)
    if (moduleManager.getExternalSystemId() != GradleConstants.SYSTEM_ID.id) return null

    val projectPath = moduleManager.getRootProjectPath() ?: return null
    val gradleProject = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath) ?: return null
    return GradleSubproject(project, gradleProject)
  }

  override fun canImportFromFile(project: Project, file: VirtualFile): Boolean {
    return canLinkAndRefreshGradleProject(file.path, project)
  }

  override fun importFromFile(project: Project, file: VirtualFile) {
    ExternalSystemUtil.confirmLoadingUntrustedProject(project, GradleConstants.SYSTEM_ID)
    linkAndRefreshGradleProject(file.path, project)
  }

  override fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings = GradleImportedProjectSettings(project)

  override fun suppressGenericImportFor(module: Module): Boolean {
    return ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId() == GradleConstants.SYSTEM_ID.id
  }
}

private class GradleImportedProjectSettings(project: Project) : ImportedProjectSettings {
  private val gradleProjectsSettings: Collection<GradleProjectSettings> = GradleSettings.getInstance(project).linkedProjectsSettings
  private val projectDir = project.guessProjectDir()

  override suspend fun applyTo(workspace: Project) {
    if (gradleProjectsSettings.isEmpty() && projectDir != null && canOpenGradleProject(projectDir)) {
      linkAndSyncGradleProject(workspace, projectDir)
      return
    }
    val targetGradleSettings = GradleSettings.getInstance(workspace)

    val specBuilder = ImportSpecBuilder(workspace, GradleConstants.SYSTEM_ID)
      .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    for (setting in gradleProjectsSettings) {
      targetGradleSettings.linkProject(setting)
      ExternalSystemUtil.refreshProject(setting.externalProjectPath, specBuilder)
    }
  }
}

private class GradleSubproject(override val workspace: Project, val gradleProject: GradleProjectSettings) : Subproject {
  private val projectInfo get() = ProjectDataManager.getInstance().getExternalProjectData(workspace, GradleConstants.SYSTEM_ID, projectPath)

  override val name: String
    get() = projectInfo?.externalProjectStructure?.data?.externalName
            ?: FileUtil.getNameWithoutExtension(gradleProject.externalProjectPath)
  override val projectPath: String get() = gradleProject.externalProjectPath

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradleSubproject

    if (workspace != other.workspace) return false
    if (gradleProject != other.gradleProject) return false

    return true
  }

  override fun hashCode(): Int {
    var result = workspace.hashCode()
    result = 31 * result + gradleProject.hashCode()
    return result
  }

  override fun getModules(): List<Module> {
    // FIXME: do not scan all modules
    return ModuleManager.getInstance(workspace).modules.filter { module ->
      val moduleManager = ExternalSystemModulePropertyManager.getInstance(module)
      moduleManager.getExternalSystemId() == GradleConstants.SYSTEM_ID.id &&
      moduleManager.getRootProjectPath() == gradleProject.externalProjectPath
    }
  }

  override fun removeSubproject() {
    GradleSettings.getInstance(workspace).unlinkExternalProject(gradleProject.externalProjectPath)
  }
}