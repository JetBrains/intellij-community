// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.project

import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider

internal class LightProjectPaneProvider : ProjectViewPaneProvider {
  override suspend fun createPanes(project: Project): List<ProjectViewPaneModel> {
    return listOf(ProjectPaneModel(project))
  }
}
