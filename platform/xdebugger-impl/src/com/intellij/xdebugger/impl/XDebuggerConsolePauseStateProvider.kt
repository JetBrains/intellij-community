// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.ExecutionConsolePauseStateProvider
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XDebuggerConsolePauseStateProvider : ExecutionConsolePauseStateProvider {
  override fun isPaused(project: Project, console: ExecutionConsole): Boolean {
    return XDebuggerManager.getInstance(project).getDebugSession(console)?.isPaused == true
  }
}
