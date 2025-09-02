// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.open

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.action.DetachExternalProjectAction.detachProject
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.updateGradleJvm
import org.jetbrains.plugins.gradle.util.validateJavaHome

internal class GradleOpenProjectProvider : AbstractOpenProjectProvider() {

  override val systemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun isProjectFile(file: VirtualFile): Boolean {
    return !file.isDirectory && GradleConstants.BUILD_FILE_EXTENSIONS.any { file.name.endsWith(it) }
  }

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    LOG.debug("Link Gradle project '$projectFile' to existing project ${project.name}")

    val projectPath = getProjectDirectory(projectFile).toNioPath()

    if (!ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProjectAsync(project, systemId, projectPath)) {
      return
    }

    val settings = createLinkSettings(projectPath, project)

    validateJavaHome(project, projectPath, settings.resolveGradleVersion())

    ExternalSystemUtil.linkExternalProject(settings, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
      .withCallback { isSuccess ->
        if (isSuccess) {
          updateGradleJvm(project, settings.externalProjectPath)
        }
      }
    )
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    val projectData = ExternalSystemApiUtil.findProjectNode(project, systemId, externalProjectPath)?.data ?: return
    withContext(Dispatchers.EDT) {
      detachProject(project, projectData.owner, projectData, null)
    }
  }
}