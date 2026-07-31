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

@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  /**
   * Filters the provided list of tool window tab [contents], returning only those that are allowed to be closed.
   *
   * This method is called before closing one or multiple tool window tabs in the editor.
   * Implementations can use this to perform pre-close checks.
   */
  @RequiresEdt
  fun filterTabsToClose(project: Project, contents: List<Content>): List<Content> = contents

  /**
   * Checks whether the given tool window tab [content] may be moved to the editor area as a tool window editor tab.
   */
  @RequiresEdt
  fun canBeMovedToEditor(content: Content): Boolean

  /**
   * Returns the current tab presentation and subsequent presentation updates.
   *
   * **Contract:** this method is called only for [content] that [canBeMovedToEditor] accepted at the moment the tab was moved to the editor.
   */
  fun getTabPresentationFlow(project: Project, content: Content): Flow<ToolWindowEditorTabPresentation>
}

@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabPresentation(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
  val tooltip: HtmlChunk? = null,
)
