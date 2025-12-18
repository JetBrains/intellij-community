// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.window

import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectViewToolWindowService {
  fun setupToolWindow(toolWindow: ToolWindow)
  suspend fun manageToolWindow(toolWindow: ToolWindow)
}
