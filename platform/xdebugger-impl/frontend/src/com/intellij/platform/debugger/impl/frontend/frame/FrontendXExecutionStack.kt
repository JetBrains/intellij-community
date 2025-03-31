// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XExecutionStackApi
import com.intellij.xdebugger.impl.rpc.XExecutionStackDto
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.XStackFramesEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FrontendXExecutionStack(
  stackDto: XExecutionStackDto,
  private val project: Project,
  private val cs: CoroutineScope,
) : XExecutionStack(stackDto.displayName, stackDto.icon?.icon()) {
  val id: XExecutionStackId = stackDto.executionStackId

  override fun getTopFrame(): XStackFrame? {
    // TODO[IJPL-177087]
    return null
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    cs.launch {
      XExecutionStackApi.getInstance().computeStackFrames(id, firstFrameIndex).collect { event ->
        when (event) {
          is XStackFramesEvent.ErrorOccurred -> {
            container.errorOccurred(event.errorMessage)
          }
          is XStackFramesEvent.XNewStackFrames -> {
            // TODO[IJPL-177087]: here we are binding FrontendXExecutionStack to the suspend context scope,
            //  which is the safest-narrowest scope in our possession.
            //  However, maybe it's possible to set up, for example, a scope that ends when another stack is selected from a combobox.
            //  But it requires further investigation.
            val feFrames = event.frames.map { FrontendXStackFrame(it, project, cs) }
            container.addStackFrames(feFrames, event.last)
          }
        }
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    return other is FrontendXExecutionStack && other.id == id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}
