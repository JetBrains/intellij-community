/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.debugger

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.frame.CallFrameView
import org.jetbrains.debugger.values.StringValue
import java.util.*

const val MAIN_LOOP_NAME = "main loop"

abstract class SuspendContextView(protected val debugProcess: MultiVmDebugProcess, protected val activeStack: ExecutionStackView) : XSuspendContext() {
  protected open val stacks by lazy {
    val childConnections = debugProcess.childConnections
    if (childConnections.isNotEmpty()) {
      val list = ArrayList<XExecutionStack>(1 + childConnections.size)

      val mainVm = debugProcess.mainVm
      if (activeStack.suspendContext.vm == mainVm) {
        list.add(activeStack)
      }
      else {
        list.add(createStackView(mainVm?.suspendContextManager?.context, MAIN_LOOP_NAME))
      }

      childConnections.mapNotNullTo(list) {
        it.vm?.let {
          val context = it.suspendContextManager.context
          if (context == activeStack.suspendContext) {
            activeStack
          }
          else {
            val displayName = it.name ?: throw IllegalStateException("Name must be not null for child VM")
            createStackView(context, displayName)
          }
        }
      }
      list.toTypedArray()
    }
    else {
      arrayOf(activeStack)
    }
  }

  private fun createStackView(context: SuspendContext<*>?, displayName: String): XExecutionStack {
    return if (context == null) {
      RunningThreadExecutionStackView(displayName)
    }
    else {
      ExecutionStackView(context, activeStack.viewSupport, null, null, displayName)
    }
  }

  override fun getActiveExecutionStack() = activeStack

  override fun getExecutionStacks(): Array<out XExecutionStack> = stacks

  fun evaluateExpression(expression: String): Promise<String> {
    val frame = activeStack.topFrame ?: return rejectedPromise("Top frame is null")
    return evaluateExpression(frame.callFrame.evaluateContext, expression)
  }

  private fun evaluateExpression(evaluateContext: EvaluateContext, expression: String) = evaluateContext.evaluate(expression)
      .thenAsync {
        val value = it.value
        if (value is StringValue && value.isTruncated) {
          value.fullString
        }
        else {
          resolvedPromise(value.valueString!!)
        }
      }
}

class RunningThreadExecutionStackView(displayName: String) : XExecutionStack(displayName, AllIcons.Debugger.ThreadRunning) {
  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
    // add dependency to DebuggerBundle?
    container?.errorOccurred("Frames not available for unsuspended thread")
  }

  override fun getTopFrame(): XStackFrame? = null
}

// icon ThreadCurrent would be preferred for active thread, but it won't be updated on stack change
class ExecutionStackView(val suspendContext: SuspendContext<*>,
                         internal val viewSupport: DebuggerViewSupport,
                         private val topFrameScript: Script?,
                         private val topFrameSourceInfo: SourceInfo? = null,
                         displayName: String = "") : XExecutionStack(displayName, AllIcons.Debugger.ThreadAtBreakpoint) {
  private var topCallFrameView: CallFrameView? = null

  override fun getTopFrame(): CallFrameView? {
    val topCallFrame = suspendContext.topFrame
    if (topCallFrameView == null || topCallFrameView!!.callFrame != topCallFrame) {
      topCallFrameView = topCallFrame?.let { CallFrameView(it, viewSupport, topFrameScript, topFrameSourceInfo, vm = suspendContext.vm) }
    }
    return topCallFrameView
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XExecutionStack.XStackFrameContainer) {
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

              val frame = frames.get(i)
              val asyncFunctionName = frame.asyncFunctionName
              if (asyncFunctionName != null) {
                result.add(AsyncFramesHeader(asyncFunctionName))
              }
              // if script is null, it is native function (Object.forEach for example), so, skip it
              val script = suspendContext.vm.scriptManager.getScript(frame)
              if (script != null) {
                val sourceInfo = viewSupport.getSourceInfo(script, frame)
                val isInLibraryContent = sourceInfo != null && viewSupport.isInLibraryContent(sourceInfo, script)
                if (isInLibraryContent && !XDebuggerSettingsManager.getInstance().dataViewSettings.isShowLibraryStackFrames) {
                  continue
                }

                result.add(CallFrameView(frame, viewSupport, script, sourceInfo, isInLibraryContent, suspendContext.vm))
              }
            }
          }
          container.addStackFrames(result, true)
        }
  }
}

private val PREFIX_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getInactiveTextColor())
private val NAME_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD, UIUtil.getInactiveTextColor())

private class AsyncFramesHeader(val asyncFunctionName: String) : XStackFrame() {
  override fun customizePresentation(component: ColoredTextContainer) {
    component.append("Async call from ", PREFIX_ATTRIBUTES)
    component.append(asyncFunctionName, NAME_ATTRIBUTES)
  }
}