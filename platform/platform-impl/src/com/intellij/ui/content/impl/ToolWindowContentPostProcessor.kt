// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
interface ToolWindowContentPostProcessor {
  fun getContentId(): ContentId
  fun isEnabled(project: Project): Boolean
  fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ToolWindowContentPostProcessor> = ExtensionPointName<ToolWindowContentPostProcessor>("com.intellij.ui.content.impl.toolWindowContentPostprocessor")
  }
}

@Internal
class ContentId(val toolWindowName: String, val contentName: String) {
  fun isSame(toolWindowName: String, contentName: String): Boolean =
    this.toolWindowName == toolWindowName && this.contentName == contentName
}