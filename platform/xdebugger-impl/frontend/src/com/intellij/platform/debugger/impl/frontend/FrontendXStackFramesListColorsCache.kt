// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.colors.color
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.psi.search.scope.NonProjectFilesScope
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.frame.XStackFramesListColorsCache
import com.intellij.xdebugger.impl.rpc.SerializedColorState
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import kotlinx.coroutines.launch
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger

internal class FrontendXStackFramesListColorsCache(
  session: FrontendXDebuggerSession,
  framesList: XDebuggerFramesList
) : XStackFramesListColorsCache(session.project) {

  private val cs = session.coroutineScope
  private val sessionId = session.id
  private val myFileColors = mutableMapOf<VirtualFileId, SerializedColorState>()
  private val myCurrentlyComputingFiles = AtomicInteger(0)

  init {
    cs.launch {
      XDebugSessionApi.getInstance().getFileColorsFlow(sessionId).collect { (fileId, colorState) ->
        val oldState = myFileColors.put(fileId, colorState)

        if (colorState is SerializedColorState.Computing) {
          myCurrentlyComputingFiles.incrementAndGet()
        }
        else if (oldState === SerializedColorState.Computing) {
          if (myCurrentlyComputingFiles.decrementAndGet() == 0) {
            framesList.repaint()
          }
        }
      }
    }
  }

  override fun get(stackFrame: XStackFrame): Color? {
    require(stackFrame is FrontendXStackFrame) { "Expected FrontendXStackFrame, got ${stackFrame::class.java}" }

    if (stackFrame.requiresCustomBackground) {
      return stackFrame.customBackgroundColor
    }

    val virtualFileId = stackFrame.fileId
    if (virtualFileId == null) {
      return colorsManager.getScopeColor(NonProjectFilesScope.NAME)
    }

    val res = myFileColors[virtualFileId]
    if (res != null) {
      return res.colorId?.color()
    }

    cs.launch {
      XDebugSessionApi.getInstance().scheduleFileColorComputation(sessionId, virtualFileId)
    }

    return null
  }
}
