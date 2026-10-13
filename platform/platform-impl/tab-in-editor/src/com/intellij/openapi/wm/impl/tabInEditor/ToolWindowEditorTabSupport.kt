// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Provides support for showing tool window content in the editor tab.
 *
 * Register an implementation using the `com.intellij.toolWindowEditorTabSupport`
 * extension point. The extension is keyed by tool window ID: the extension `key`
 * must match the ID of the tool window.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  /**
   * Filters the provided list of tool window tab [contents], returning only those that are allowed
   * to be closed.
   *
   * This method is called before tool window tabs are closed in the editor.
   * It is used to perform pre-close checks and exclude tabs that must remain open.
   *
   * **Contract:**
   * 1. All items in [contents] belong to the same tool window as this [ToolWindowEditorTabSupport].
   * 2. Every item in [contents] was accepted by [canBeMovedToEditor] when it was moved to the editor.
   */
  @RequiresEdt
  fun filterTabsToClose(project: Project, contents: List<Content>): List<Content> = contents

  /**
   * Returns whether the given tool window tab [content] may be moved to the editor area.
   */
  @RequiresEdt
  fun canBeMovedToEditor(content: Content): Boolean

  /**
   * Returns a flow containing the current tab presentation and all subsequent presentation updates.
   *
   * **Contract:** this method is called only for [content] that was accepted by
   * [canBeMovedToEditor] when it was moved to the editor.
   */
  fun getTabPresentationFlow(project: Project, content: Content): Flow<ToolWindowEditorTabPresentation>
}

/**
 * Describes the presentation of tool window content shown in the editor tab.
 *
 * Instances are emitted by [ToolWindowEditorTabSupport.getTabPresentationFlow].
 * Each emitted value replaces the current presentation of the corresponding editor tab.
 *
 * @param title the title of the editor tab
 * @param icon the icon of the editor tab, or `null` if no icon should be shown
 * @param tooltip the tooltip of the editor tab, or `null` if no tooltip should be shown
 */
@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabPresentation(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
  val tooltip: HtmlChunk? = null,
)
