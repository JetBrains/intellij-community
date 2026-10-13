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

  /**
   * Saves the current state of the pane into the specified XML element.
   * 
   * A pane may choose not to save its state if it's empty.
   * The definition of empty is up to the pane's implementation.
   * It may not be actually empty, but contain some "service" nodes,
   * such as, for example, External Libraries.
   * 
   * Normally, an empty state indicates a pane that was not fully initialized,
   * and therefore overwriting the previously saved state could be undesirable.
   * This is especially important in remdev scenarios when initialization can take a while.
   * 
   * If `false` is returned, then this function should not write any state to the `element`,
   * but even if it does, this state will be ignored.
   * 
   * @return `true` iff the state should be persisted
   */
  @RequiresEdt
  fun saveStateTo(element: Element): Boolean

  @RequiresEdt
  fun restoreStateFrom(element: Element?, options: RestorePaneStateOptions)

  suspend fun manage()
}

@ApiStatus.Experimental
interface RestorePaneStateOptions {
  val restoreSelection: Boolean
}

@ApiStatus.Internal
data class RestorePaneStateOptionsImpl(
  override val restoreSelection: Boolean,
) : RestorePaneStateOptions

@get:ApiStatus.Internal
val FrontendProjectViewPane.id: ProjectViewPaneId
  get() = (descriptor as ProjectViewPaneDescriptorImpl).id
