// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.toolWindow.ToolWindowPane
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Helper class which sets up a main IDE frame
 */
internal class IdeProjectFrameHelper(
  frame: IdeFrameImpl,
  loadingState: FrameLoadingState,
) : ProjectFrameHelper(frame, loadingState) {
  @get:RequiresEdt
  val toolWindowPane: ToolWindowPane
    get() = rootPane.getToolWindowPane()
}