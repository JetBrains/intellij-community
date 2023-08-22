// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.JBColor

class RunToolbarDebugProcess : RunToolbarProcess {
  companion object {
    internal val pillColor = JBColor.namedColor("RunToolbar.Debug.activeBackground", JBColor(0xFFE7A8, 0x604809))
  }

  override val ID: String = ToolWindowId.DEBUG
  override val executorId: String = ID
  override val name: String = ExecutionBundle.message("run.toolbar.debugging")
  override val shortName: String = ExecutionBundle.message("run.toolbar.debug")

  override val actionId: String = "RunToolbarDebugProcess"
  override val moreActionSubGroupName: String = "RunToolbarDebugMoreActionSubGroupName"

  override val showInBar: Boolean = true

  override val pillColor: JBColor = RunToolbarDebugProcess.pillColor
}

class RunToolbarAttachDebugProcess : RunToolbarProcess {
  override val ID: String = "ProcessAttachDebug"
  override val executorId: String = ID
  override val name: String = ExecutionBundle.message("run.toolbar.attached")
  override val shortName: String = name

  override val actionId: String = "RunToolbarAttachDebugProcess"
  override val moreActionSubGroupName: String = "RunToolbarAttachDebugMoreActionSubGroupName"

  override val showInBar: Boolean = false

  override fun isTemporaryProcess(): Boolean = true

  override val pillColor: JBColor = RunToolbarDebugProcess.pillColor
}