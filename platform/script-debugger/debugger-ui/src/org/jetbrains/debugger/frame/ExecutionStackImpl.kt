/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger.frame

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.debugger.DebuggerViewSupport
import org.jetbrains.debugger.Script
import org.jetbrains.debugger.SuspendContext
import org.jetbrains.debugger.done
import java.util.*

internal class ExecutionStackImpl(private val suspendContext: SuspendContext, private val viewSupport: DebuggerViewSupport, private val topFrameScript: Script?) : XExecutionStack("") {
  private var topCallFrameView: CallFrameView? = null

  override fun getTopFrame(): CallFrameView? {
    val topCallFrame = suspendContext.topFrame
    if (topCallFrameView == null || topCallFrameView!!.callFrame != topCallFrame) {
      topCallFrameView = if (topCallFrame == null) null else CallFrameView(topCallFrame, viewSupport, topFrameScript)
    }
    return topCallFrameView
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XExecutionStack.XStackFrameContainer) {
    val suspendContext = viewSupport.vm!!.suspendContextManager.context ?: return
    // WipSuspendContextManager set context to null on resume _before_ vm.getDebugListener().resumed() call() (in any case, XFramesView can queue event to EDT), so, IDE state could be outdated compare to VM (our) state
    suspendContext.frames
      .done(suspendContext) { frames ->
        val count = frames.size - firstFrameIndex
        val result: List<XStackFrame>
        if (count < 1) {
          result = emptyList()
        }
        else {
          result = ArrayList<XStackFrame>(count)
          for (i in firstFrameIndex..frames.size - 1) {
            if (i == 0) {
              result.add(topFrame!!)
              continue
            }

            val frame = frames[i]
            // if script is null, it is native function (Object.forEach for example), so, skip it
            val script = suspendContext.valueManager.vm.scriptManager.getScript(frame)
            if (script != null) {
              val sourceInfo = viewSupport.getSourceInfo(script, frame)
              val isInLibraryContent = sourceInfo != null && viewSupport.isInLibraryContent(sourceInfo, script)
              if (isInLibraryContent && !XDebuggerSettingsManager.getInstance().dataViewSettings.isShowLibraryStackFrames) {
                continue
              }

              result.add(CallFrameView(frame, viewSupport, script, sourceInfo, isInLibraryContent))
            }
          }
        }
        container.addStackFrames(result, true)
      }
  }
}