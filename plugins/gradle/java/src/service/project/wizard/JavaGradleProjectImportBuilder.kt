// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl.setupCreatedProject
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.DeprecatedProjectBuilderForImport
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectImportProvider.getDefaultPath
import com.intellij.projectImport.ProjectOpenProcessor
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectOpenProcessor
import org.jetbrains.plugins.gradle.service.project.open.canLinkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.Icon

/**
 * Do not use this project import builder directly.
 *
 * Internal stable Api
 * Use [com.intellij.ide.actions.ImportModuleAction.createFromWizard] to import (attach) a new project.
 * Use [com.intellij.ide.impl.ProjectUtil.openOrImport] to open (import) a new project.
 *
 * Internal experimental Api
 * Use [org.jetbrains.plugins.gradle.service.project.open.openGradleProject] to open (import) a new gradle project.
 * Use [org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject] to attach a gradle project to an opened idea project.
 */
internal class JavaGradleProjectImportBuilder : ProjectImportBuilder<Any>(), DeprecatedProjectBuilderForImport {
  override fun getName(): String = GradleBundle.message("gradle.name")

  override fun getIcon(): Icon = GradleIcons.Gradle

  override fun getList(): List<Any> = emptyList()

  override fun isMarked(element: Any): Boolean = true

  override fun setOpenProjectSettingsAfter(on: Boolean) {}

  private fun getPathToBeImported(path: String): String {
    val localForImport = LocalFileSystem.getInstance()
    val file = localForImport.refreshAndFindFileByPath(path)
    return file?.let(::getDefaultPath) ?: path
  }

  override fun setFileToImport(path: String) = super.setFileToImport(getPathToBeImported(path))

  override fun createProject(name: String?, path: String): Project? {
    return setupCreatedProject(super.createProject(name, path))?.also {
      it.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true)
    }
  }

  override fun validate(currentProject: Project?, project: Project): Boolean {
    return canLinkAndRefreshGradleProject(fileToImport, project)
  }

  override fun commit(project: Project,
                      model: ModifiableModuleModel?,
                      modulesProvider: ModulesProvider?,
                      artifactModel: ModifiableArtifactModel?): List<Module> {
    linkAndRefreshGradleProject(fileToImport, project)
    return emptyList()
  }

  override fun getProjectOpenProcessor() =
    ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(GradleProjectOpenProcessor::class.java)
}