// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.stateWidget

import com.intellij.execution.segmentedRunDebugWidget.StateWidgetManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl

class StateWidgetXDebugUtils {
  companion object {

    @JvmStatic
    fun isAvailable(project: Project?): Boolean {
      project?.let {
        val stateWidgetManager = StateWidgetManager.getInstance(it)
        if (stateWidgetManager.getExecutionsCount() == 1 && stateWidgetManager.getActiveProcesses().firstOrNull()?.ID == ToolWindowId.DEBUG) {
          return true
        }
      }
      return false
    }

    @JvmStatic
    fun getSingleSession(project: Project?): XDebugSessionImpl? {
      project?.let { proj ->
        if (isAvailable(proj)) {
          val session = XDebuggerManager.getInstance(proj)?.debugSessions?.filter { !it.isStopped }?.filterNotNull()?.firstOrNull()
          if (session is XDebugSessionImpl) {
            return session
          }
        }
      }
      return null
    }
  }
}