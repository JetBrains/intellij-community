// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.legacy

import com.intellij.ide.SelectInTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.actions.SplitProjectViewSelectInTarget
import com.intellij.platform.projectView.frontend.impl.pane.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import org.jetbrains.annotations.NonNls

internal class LegacyFrontendProjectViewPaneProvider : FrontendProjectViewPaneProvider {
  override fun createPane(
    project: Project,
    descriptor: ProjectViewPaneDescriptor
  ): FrontendProjectViewPane = LegacyFrontendProjectViewPane(project, descriptor)
}

internal class LegacyFrontendProjectViewPane(project: Project, descriptor: ProjectViewPaneDescriptor) : TreeBasedFrontendProjectViewPane(project) {
  override val id: ProjectViewPaneId = descriptor.id

  override val displayName: @NlsSafe String = descriptor.presentableName

  override val order: Int = descriptor.order

  override val selectInTargets: Collection<SelectInTarget> = descriptor.selectInTargetDescriptors.map {
    SplitProjectViewSelectInTarget(
      minorViewId = it.id,
      presentableName = it.presentableName,
      weight = it.weight
    )
  }
}
