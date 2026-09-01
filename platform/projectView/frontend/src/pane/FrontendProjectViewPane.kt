// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.pane

import com.intellij.ide.SelectInTarget
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
interface FrontendProjectViewPaneModel {
  suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor

  @RequiresEdt
  fun createPane(descriptor: ProjectViewPaneDescriptor): FrontendProjectViewPane
}

@ApiStatus.Experimental
interface FrontendProjectViewPane {
  val descriptor: ProjectViewPaneDescriptor

  val component: JComponent

  val componentToFocus: JComponent

  var isCurrent: Boolean
  
  val selectInTargets: Collection<SelectInTarget>
  
  @RequiresEdt
  fun saveStateTo(element: Element)

  @RequiresEdt
  fun restoreStateFrom(element: Element)

  suspend fun manage()
}

@get:ApiStatus.Internal
val FrontendProjectViewPane.id: ProjectViewPaneId
  get() = (descriptor as ProjectViewPaneDescriptorImpl).id
