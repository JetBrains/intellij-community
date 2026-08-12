// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.ide.SelectInTarget
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.ReceiveChannel
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface FrontendProjectViewPane {
  val descriptor: ProjectViewPaneDescriptorImpl

  val displayName: @NlsSafe String

  val component: JComponent

  val requestChannel: ReceiveChannel<ProjectViewPaneRequest>

  var isCurrent: Boolean
  
  val selectInTargets: Collection<SelectInTarget>

  suspend fun applyStateChange(event: ProjectViewPaneStateEvent)
  
  @RequiresEdt
  fun saveStateTo(element: Element)

  @RequiresEdt
  fun restoreStateFrom(element: Element)

  suspend fun manage()
  
  fun getOptionSupport(): ProjectViewActionSupport
}

@get:ApiStatus.Internal
val FrontendProjectViewPane.id: ProjectViewPaneId
  get() = descriptor.id
