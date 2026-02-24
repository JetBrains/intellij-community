// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl.legacy

import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.frontend.impl.pane.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProvider
import com.intellij.platform.projectView.impl.legacy.LEGACY_PROVIDER_ID
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import org.jetbrains.annotations.NonNls

internal class LegacyFrontendProjectViewPaneProvider : FrontendProjectViewPaneProvider {
  override val id: ProjectViewPaneProviderId
    get() = LEGACY_PROVIDER_ID

  override fun createPane(
    project: Project,
    descriptor: ProjectViewPaneDescriptor
  ): FrontendProjectViewPane = LegacyFrontendProjectViewPane(project, descriptor)
}

internal class LegacyFrontendProjectViewPane(project: Project, descriptor: ProjectViewPaneDescriptor) : TreeBasedFrontendProjectViewPane(project) {
  override val providerId: ProjectViewPaneProviderId
    get() = LEGACY_PROVIDER_ID

  override val id: ProjectViewPaneId = descriptor.id

  override val displayName: @NonNls String = descriptor.presentableName

  override val order: Int = descriptor.order
}
