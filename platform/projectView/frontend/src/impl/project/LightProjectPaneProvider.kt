// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.project

import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class LightProjectPaneProvider : ProjectViewPaneProvider {
  override fun createPanes(project: Project): Flow<List<ProjectViewPaneModel>> {
    return flowOf(listOf(LightProjectPaneModel(project)))
  }
}

private class LightProjectPaneModel(project: Project) : ProjectPaneModel(project) {
  override fun supportsOption(option: ProjectViewPaneOption): Boolean {
    return when (option) {
      is ProjectViewPaneOption.ShowMembers -> false // no required plugins on the frontend
      else -> super.supportsOption(option)
    }
  }
}
