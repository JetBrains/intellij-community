// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.NestingRuleDTO
import com.intellij.platform.projectView.pane.ProjectViewPaneSettingsStateDTO
import com.intellij.platform.projectView.pane.ProjectViewPaneOptionDTO
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectViewActionSupport {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewActionSupport = ProjectViewToolWindowService.getInstance(project).getActionSupport()
  }

  fun getActionState(): ProjectViewPaneSettingsStateDTO?

  fun getActionStateFlow(): Flow<ProjectViewPaneSettingsStateDTO?>

  fun requestOptionValueChange(option: ProjectViewPaneOptionDTO, newValue: Boolean)

  fun requestSortKeyChange(sortKey: NodeSortKey)

  fun requestFileNestingChange(fileNestingOn: Boolean, activeRules: List<NestingRuleDTO>)
}
