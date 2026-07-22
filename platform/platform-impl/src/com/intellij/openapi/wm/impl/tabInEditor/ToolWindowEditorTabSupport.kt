// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabSupport {
  fun getEditorTabDescriptor(toolWindow: ToolWindow, content: Content): ToolWindowEditorTabDescriptor?

  fun canCloseFile(project: Project, content: Content): Boolean = true
}

@ApiStatus.Experimental
@ApiStatus.Internal
data class ToolWindowEditorTabDescriptor(
  val title: @NlsContexts.TabTitle String,
  val icon: Icon? = null,
)
