// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import kotlinx.coroutines.channels.ReceiveChannel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

internal val FrontendProjectViewPaneProviderEP = ExtensionPointName.create<FrontendProjectViewPaneProvider>("com.intellij.project.view.pane.frontend")

@ApiStatus.Internal
interface FrontendProjectViewPaneProvider {
  val id: ProjectViewPaneProviderId
  fun createPane(id: ProjectViewPaneId): FrontendProjectViewPane
}

@ApiStatus.Internal
interface FrontendProjectViewPane {
  val id: ProjectViewPaneId

  val displayName: @NonNls String
  
  val component: JComponent

  val requestChannel: ReceiveChannel<ProjectViewPaneRequest>
  
  fun applyStateChange(event: ProjectViewPaneStateEvent)
}
