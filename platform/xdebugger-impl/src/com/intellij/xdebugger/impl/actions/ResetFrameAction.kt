// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ResetFrameAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    withHandler(e, true) { handler, stackFrame -> handler.drop(stackFrame) }
  }

  override fun update(e: AnActionEvent) {
    DebuggerUIUtil.setActionEnabled(e, withHandler(e, false) { handler, stackFrame -> handler.canDropFrame(stackFrame) == ThreeState.YES })
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private fun <T> withHandler(e: AnActionEvent, default: T, action: (XDropFrameHandler, XStackFrame) -> T): T {
    val session = DebuggerUIUtil.getSession(e)
    if (session != null) {
      val dropFrameHandler = session.debugProcess.dropFrameHandler
      if (dropFrameHandler != null) {
        val currentFrame = session.currentStackFrame
        if (currentFrame != null) {
          return action(dropFrameHandler, currentFrame)
        }
      }
    }
    return default
  }
}