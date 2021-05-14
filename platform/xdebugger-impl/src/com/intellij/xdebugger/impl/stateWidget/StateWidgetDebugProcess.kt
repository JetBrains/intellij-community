// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.stateWidget

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess.Companion.isRerunAvailable
import com.intellij.openapi.wm.ToolWindowId

class StateWidgetDebugProcess : StateWidgetProcess {
  override val ID: String = ToolWindowId.DEBUG
  override val executorId: String = ToolWindowId.DEBUG
  override val name: String = ExecutionBundle.message("state.widget.debug")

  override val actionId: String = "StateWidgetDebugProcess"
  override val moreActionGroupName: String = "StateWidgetDebugMoreActionGroupName"
  override val moreActionSubGroupName: String = "StateWidgetDebugMoreActionSubGroupName"

  override val showInBar: Boolean = true
  override fun rerunAvailable(): Boolean = isRerunAvailable()
}