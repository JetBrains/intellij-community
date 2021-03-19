// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.UntrustedProjectModeProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleUntrustedModeProvider: UntrustedProjectModeProvider {

  override val systemId = GradleConstants.SYSTEM_ID

  override fun shouldShowEditorNotification(project: Project): Boolean {
    val settings = GradleSettings.getInstance(project)
    return settings.linkedProjectsSettings.isNotEmpty()
  }

  override fun loadAllLinkedProjects(project: Project) {
    val settings = GradleSettings.getInstance(project)
    for (linkedProjectSettings in settings.linkedProjectsSettings) {
      val externalProjectPath = linkedProjectSettings.externalProjectPath
      ExternalSystemUtil.refreshProject(externalProjectPath, ImportSpecBuilder(project, systemId))
    }
  }
}