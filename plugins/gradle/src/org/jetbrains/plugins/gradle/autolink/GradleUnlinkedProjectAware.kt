// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.service.project.open.GradleOpenProjectProvider
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun buildFileExtensions(): Array<String> = GradleConstants.BUILD_FILE_EXTENSIONS

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean = buildFile.name in GradleConstants.KNOWN_GRADLE_FILES

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val gradleSettings = GradleSettings.getInstance(project)
    val projectSettings = gradleSettings.getLinkedProjectSettings(externalProjectPath)
    return projectSettings != null
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    val gradleSettings = GradleSettings.getInstance(project)
    gradleSettings.subscribe(object : GradleSettingsListener {
      override fun onProjectsLinked(settings: Collection<GradleProjectSettings>) =
        settings.forEach { listener.onProjectLinked(it.externalProjectPath) }

      override fun onProjectsUnlinked(linkedProjectPaths: Set<String>) =
        linkedProjectPaths.forEach { listener.onProjectUnlinked(it) }
    }, parentDisposable)
  }

  override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    linkAndSyncGradleProject(project, externalProjectPath)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    GradleOpenProjectProvider().unlinkProject(project, externalProjectPath)
  }
}
