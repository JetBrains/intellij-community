// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneProvider
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class ScopePaneProvider : ProjectViewPaneProvider {
  override fun createPanes(project: Project): Flow<List<ProjectViewPaneModel>> {
    // The same IDEs where the legacy ScopeViewPane extension isn't applicable.
    if (PlatformUtils.isPyCharmEducational() || PlatformUtils.isRider()) return flowOf(emptyList())
    return ScopePaneService.getInstance(project).panes
  }
}
