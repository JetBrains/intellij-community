// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.KNOWN_GRADLE_FILES
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

class GradleUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId = SYSTEM_ID

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean {
    return buildFile.name in KNOWN_GRADLE_FILES
  }

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val gradleSettings = GradleSettings.getInstance(project)
    val projectSettings = gradleSettings.getLinkedProjectSettings(externalProjectPath)
    return projectSettings != null
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    val gradleSettings = GradleSettings.getInstance(project)
    gradleSettings.subscribe(object : GradleSettingsListenerAdapter() {
      override fun onProjectsLinked(settings: Collection<GradleProjectSettings>) =
        settings.forEach { listener.onProjectLinked(it.externalProjectPath) }

      override fun onProjectsUnlinked(linkedProjectPaths: Set<String>) =
        linkedProjectPaths.forEach { listener.onProjectUnlinked(it) }
    }, parentDisposable)
  }

  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    linkAndRefreshGradleProject(externalProjectPath, project)
  }
}