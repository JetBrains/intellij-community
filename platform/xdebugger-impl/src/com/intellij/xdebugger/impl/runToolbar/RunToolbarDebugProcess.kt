// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.JBColor

class RunToolbarDebugProcess : RunToolbarProcess {
  override val ID: String = ToolWindowId.DEBUG
  override val executorId: String = ToolWindowId.DEBUG
  override val name: String = ExecutionBundle.message("run.toolbar.debug")

  override val actionId: String = "RunToolbarDebugProcess"
  override val moreActionSubGroupName: String = "RunToolbarDebugMoreActionSubGroupName"

  override val showInBar: Boolean = true

  override val pillColor: JBColor =  JBColor.namedColor("RunToolbar.Debug.activeBackground", JBColor(0xFAD576, 0xFAD576))
}