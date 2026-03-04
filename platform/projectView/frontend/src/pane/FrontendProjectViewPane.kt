// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.ReceiveChannel
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

internal val FrontendProjectViewPaneProviderEP = ExtensionPointName.create<FrontendProjectViewPaneProvider>("com.intellij.project.view.pane.frontend")

@ApiStatus.Internal
interface FrontendProjectViewPaneProvider {
  val id: ProjectViewPaneProviderId
  fun createPane(project: Project, descriptor: ProjectViewPaneDescriptor): FrontendProjectViewPane
}

@ApiStatus.Internal
interface FrontendProjectViewPane {
  val providerId: ProjectViewPaneProviderId

  val id: ProjectViewPaneId

  val displayName: @NonNls String

  val component: JComponent

  val order: Int

  val requestChannel: ReceiveChannel<ProjectViewPaneRequest>

  var isCurrent: Boolean

  @RequiresEdt
  fun applyStateChange(event: ProjectViewPaneStateEvent)
  
  @RequiresEdt
  fun saveStateTo(element: Element)

  @RequiresEdt
  fun restoreStateFrom(element: Element)

  suspend fun manage()
  
  fun getOptionSupport(): ProjectViewActionSupport
}
