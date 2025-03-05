// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("Don't use this extension point. For RD purposes only.")
interface ToolWindowContentPostProcessor {
  fun isEnabled(project: Project, content: Content, toolWindow: ToolWindow): Boolean
  fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ToolWindowContentPostProcessor> = ExtensionPointName("com.intellij.ui.content.impl.toolWindowContentPostprocessor")
  }
}