// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.content.Content
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  fun canCloseFile(project: Project, content: Content): Boolean = true

  /**
   * Returns the current tab descriptor and subsequent presentation updates.
   */
  fun getTabDescriptorState(project: Project, content: Content): StateFlow<ToolWindowEditorTabDescriptor>
}

@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabDescriptor(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
)
