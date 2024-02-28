// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleAdvancedSettingsListener : AdvancedSettingsChangeListener {

  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if ("gradle.download.sources" != id || newValue !is Boolean || !newValue) {
      return
    }
    ProjectManager.getInstance().openProjects
      .forEach { markGradleProjectDirty(it) }
  }

  private fun markGradleProjectDirty(project: Project) {
    if (project.isDisposed) {
      return
    }
    val projectBasePath = project.basePath ?: return
    val settings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)
    val linkedProjectSettings = settings.getLinkedProjectSettings(projectBasePath) ?: return
    val tracker = ExternalSystemProjectTracker.getInstance(project)
    tracker.markDirty(ExternalSystemProjectId(GradleConstants.SYSTEM_ID, linkedProjectSettings.externalProjectPath))
    tracker.scheduleProjectRefresh()
  }
}