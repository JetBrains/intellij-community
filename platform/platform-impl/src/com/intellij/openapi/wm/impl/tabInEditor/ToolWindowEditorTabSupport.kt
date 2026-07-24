// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  @RequiresEdt
  fun canCloseTab(project: Project, content: Content): Boolean = true

  /**
   * Returns the current tab presentation and subsequent presentation updates.
   */
  fun getTabPresentationState(project: Project, content: Content): StateFlow<ToolWindowEditorTabPresentation>
}

@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabPresentation(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
)
