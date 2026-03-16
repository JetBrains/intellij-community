// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.toolWindow.ToolWindowLayoutApplyMode
import com.intellij.toolWindow.ToolWindowLayoutProfile
import com.intellij.toolWindow.ToolWindowLayoutProfileService

private const val TOOL_WINDOW_LAYOUT_MIGRATION_PROPERTY_PREFIX = "toolwindow.layout.profile.migration."

internal data class ProjectFrameToolWindowLayoutProfile(
  val profileId: String,
  val profile: ToolWindowLayoutProfile,
)

internal fun resolveProjectFrameToolWindowLayoutProfile(
  project: Project,
  isNewUi: Boolean,
): ProjectFrameToolWindowLayoutProfile? {
  val uiPolicy = service<ProjectFrameCapabilitiesService>().getUiPolicy(project) ?: return null
  val profileId = uiPolicy.toolWindowLayoutProfileId ?: return null
  val profile = service<ToolWindowLayoutProfileService>().getProfile(project = project, profileId = profileId, isNewUi = isNewUi)
                ?: return null
  return ProjectFrameToolWindowLayoutProfile(profileId = profileId, profile = profile)
}

internal fun applyProjectFrameLayoutPolicy(
  projectFrameLayoutProfile: ProjectFrameToolWindowLayoutProfile?,
  scheduleSetLayout: (DesktopLayout) -> Unit,
) {
  val (profileId, profile) = projectFrameLayoutProfile ?: return
  when (profile.applyMode) {
    ToolWindowLayoutApplyMode.SEED_ONLY -> return
    ToolWindowLayoutApplyMode.FORCE_ONCE -> applyProjectFrameLayoutOnce(
      profileId = profileId,
      profile = profile,
      scheduleSetLayout = scheduleSetLayout,
    )
  }
}

private fun applyProjectFrameLayoutOnce(
  profileId: String,
  profile: ToolWindowLayoutProfile,
  scheduleSetLayout: (DesktopLayout) -> Unit,
) {
  val migrationVersion = profile.migrationVersion
  if (migrationVersion <= 0) {
    return
  }

  val propertiesComponent = PropertiesComponent.getInstance()
  val migrationKey = TOOL_WINDOW_LAYOUT_MIGRATION_PROPERTY_PREFIX + profileId
  val appliedMigrationVersion = propertiesComponent.getInt(migrationKey, 0)
  if (appliedMigrationVersion >= migrationVersion) {
    return
  }

  scheduleSetLayout(profile.layout)
  propertiesComponent.setValue(migrationKey, migrationVersion, 0)
}
