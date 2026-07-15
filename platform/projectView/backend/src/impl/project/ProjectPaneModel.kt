// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.project

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.impl.TreeStructureBasedProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.pane.projectViewPaneId

internal class ProjectPaneProvider : ProjectViewPaneProvider {
  override suspend fun createPanes(project: Project): List<ProjectViewPaneModel> {
    return listOf(ProjectPaneModel(project))
  }
}

internal class ProjectPaneModel(project: Project) : TreeStructureBasedProjectViewPaneModel(project) {
  override suspend fun id(): ProjectViewPaneId = projectViewPaneId(ProjectViewPane.ID)

  override suspend fun presentableName(): @NlsSafe String = ProjectViewBackendBundle.message("project.view.pane.project.title")

  override suspend fun order(): Int = 0

  override fun createTreeStructure(viewSettings: ViewSettings): AbstractProjectTreeStructure {
    return ProjectPaneTreeStructure(project, viewSettings)
  }
}

private class ProjectPaneTreeStructure(project: Project, viewSettings: ViewSettings) : AbstractProjectTreeStructure(project, viewSettings)
