// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Customizes the tool window of the split Project View.
 *
 * A product or a plugin registers an implementation in the `com.intellij.project.view.tool.window.customizer` extension point.
 */
@ApiStatus.Experimental
interface ProjectViewToolWindowCustomizer {
  /**
   * Customizes [toolWindow] when the split Project View sets it up.
   *
   * The split Project View calls this function once for each tool window.
   * The call comes after the split Project View sets its defaults, such as [ToolWindowContentUiType.COMBO], and before it adds the first content.
   * So an implementation can replace a default.
   * For example, `toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)` shows the panes as tabs.
   */
  @RequiresEdt
  fun customize(toolWindow: ToolWindow)
}

internal val PROJECT_VIEW_TOOL_WINDOW_CUSTOMIZER_EP: ExtensionPointName<ProjectViewToolWindowCustomizer> =
  ExtensionPointName("com.intellij.project.view.tool.window.customizer")
